package com.aicoach.resume.service;

import com.aicoach.ai.AiTypes.AiPurpose;
import com.aicoach.ai.AiTypes.CallContext;
import com.aicoach.ai.schema.ParsedResume;
import com.aicoach.ai.service.AiGateway;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.security.Hashing;
import com.aicoach.event.UserDeletedEvent;
import com.aicoach.resume.domain.Resume;
import com.aicoach.resume.dto.ResumeResponse;
import com.aicoach.resume.repository.ResumeContentRepository;
import com.aicoach.resume.repository.ResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resume upload and parsing: validate at the boundary (PDF magic bytes, ≤ 5 MB, ≤ 10 pages, has text), extract text
 * with PDFBox, then ask the AI for a structured profile on a background thread. Extracted skills and projects are
 * grounded: anything not literally present in the resume text is dropped and flagged.
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_PAGES = 10;
    static final int MIN_TEXT_CHARS = 200;
    static final int MAX_TEXT_CHARS = 30_000;
    static final int MAX_RESUMES = 5;
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final ResumeRepository resumes;
    private final ResumeContentRepository content;
    private final AiGateway ai;
    private final ObjectMapper json;
    private final Validator validator;
    private final ExecutorService executor;
    private final TransactionTemplate tx;

    public ResumeService(ResumeRepository resumes, ResumeContentRepository content, AiGateway ai, ObjectMapper json,
            Validator validator, @Qualifier("interviewExecutor") ExecutorService executor,
            PlatformTransactionManager transactionManager) {
        this.resumes = resumes;
        this.content = content;
        this.ai = ai;
        this.json = json;
        this.validator = validator;
        this.executor = executor;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ upload

    public ResumeResponse upload(UUID userId, MultipartFile file) {
        byte[] bytes = readValidated(file);
        int pages = pageCount(bytes);
        if (resumes.countByUserId(userId) >= MAX_RESUMES) {
            throw new AppException(ErrorCode.CONFLICT, "You can keep up to " + MAX_RESUMES + " resumes; delete one first");
        }
        String filename = sanitiseFilename(file.getOriginalFilename());
        UUID id = tx.execute(status -> {
            UUID created = content.insert(userId, filename, bytes, Hashing.sha256Hex(bytes), pages);
            Resume resume = resumes.findById(created).orElseThrow();
            resume.startParsing();
            if (resumes.findByUserIdAndActiveTrue(userId).isEmpty()) {
                resume.setActive(true);
            }
            resumes.save(resume);
            return created;
        });
        executor.submit(() -> parse(id));
        return view(resumes.findById(id).orElseThrow());
    }

    static byte[] readValidated(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Choose a PDF file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.PAYLOAD_TOO_LARGE, "The resume must be 5 MB or smaller");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "The upload could not be read");
        }
        if (!isPdf(bytes)) {
            throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Only PDF resumes are supported");
        }
        return bytes;
    }

    static boolean isPdf(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    static int pageCount(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.isEncrypted()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Password-protected PDFs are not supported");
            }
            int pages = doc.getNumberOfPages();
            if (pages > MAX_PAGES) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "The resume must have at most " + MAX_PAGES + " pages");
            }
            return pages;
        } catch (IOException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "The file is not a readable PDF");
        }
    }

    static String extractText(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc).replace("\u0000", "").replaceAll("[ \\t]+", " ")
                    .replaceAll("\\n{3,}", "\n\n").trim();
            return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
        }
    }

    static String sanitiseFilename(String name) {
        String base = name == null ? "resume.pdf" : name.replaceAll("^.*[/\\\\]", "");
        base = base.replaceAll("[^A-Za-z0-9 ._()-]", "_");
        if (base.isBlank()) {
            base = "resume.pdf";
        }
        return base.length() > 120 ? base.substring(0, 120) : base;
    }

    // ------------------------------------------------------------------ parsing (background)

    void parse(UUID id) {
        Resume resume = resumes.findById(id).orElse(null);
        if (resume == null) {
            return;
        }
        try {
            String text = extractText(content.content(id));
            if (text.length() < MIN_TEXT_CHARS) {
                fail(id, "We couldn't read enough text from this PDF. Scanned/image-only resumes aren't supported; "
                        + "export a text-based PDF instead.");
                return;
            }
            ParsedResume parsed = ai.structured("resume-extraction", java.util.Map.of("resumeText", text,
                    "outputSchema", ParsedResume.SCHEMA), ParsedResume.class,
                    new CallContext(resume.getUserId(), null, AiPurpose.RESUME_EXTRACTION, id.getMostSignificantBits()))
                    .value();
            if ("NOT_A_RESUME".equals(parsed.summary())) {
                fail(id, "This document doesn't look like a resume.");
                return;
            }
            List<String> flags = new ArrayList<>();
            ParsedResume grounded = ground(parsed, text, flags);
            ArrayNode integrity = json.createArrayNode();
            flags.forEach(integrity::add);
            if (parsed.integrityFlags() != null) {
                parsed.integrityFlags().forEach(integrity::add);
            }
            tx.executeWithoutResult(status -> resumes.findById(id).ifPresent(r -> {
                r.parsed(text, json.valueToTree(grounded), integrity);
                resumes.save(r);
            }));
        } catch (AppException e) {
            fail(id, "Parsing failed: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Resume {} could not be parsed", id, e);
            fail(id, "The PDF could not be processed.");
        }
    }

    /** Drops skills/projects that don't appear in the resume text (hallucination guard). */
    static ParsedResume ground(ParsedResume parsed, String text, List<String> flags) {
        String haystack = text.toLowerCase(Locale.ROOT);
        List<ParsedResume.Skill> skills = new ArrayList<>();
        for (ParsedResume.Skill skill : parsed.skills()) {
            if (mentioned(haystack, skill.name())) {
                skills.add(skill);
            } else {
                flags.add("Dropped skill not found in the resume text: " + skill.name());
            }
        }
        List<ParsedResume.Project> projects = new ArrayList<>();
        for (ParsedResume.Project project : parsed.projects()) {
            boolean found = mentioned(haystack, project.name())
                    || java.util.Arrays.stream(project.name().split("\\s+")).filter(w -> w.length() >= 4)
                            .anyMatch(w -> mentioned(haystack, w));
            if (found) {
                projects.add(new ParsedResume.Project(project.name(), project.summary(),
                        project.techStack().stream().filter(t -> mentioned(haystack, t)).toList(), project.highlights()));
            } else {
                flags.add("Dropped project not found in the resume text: " + project.name());
            }
        }
        return new ParsedResume(parsed.summary(), skills, projects, parsed.experience(), List.of());
    }

    static boolean mentioned(String haystack, String term) {
        String t = term.toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) {
            return false;
        }
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(t) + "(?![a-z0-9])").matcher(haystack).find();
    }

    private void fail(UUID id, String message) {
        tx.executeWithoutResult(status -> resumes.findById(id).ifPresent(r -> {
            r.failed(message);
            resumes.save(r);
        }));
    }

    // ------------------------------------------------------------------ queries and commands

    @Transactional(readOnly = true)
    public List<ResumeResponse> list(UUID userId) {
        return resumes.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse get(UUID userId, UUID id) {
        return view(require(userId, id));
    }

    @Transactional
    public ResumeResponse correct(UUID userId, UUID id, ParsedResume corrected) {
        Resume resume = require(userId, id);
        if (resume.getStatus() != Resume.Status.PARSED) {
            throw new AppException(ErrorCode.CONFLICT, "The resume has not been parsed yet");
        }
        Set<ConstraintViolation<ParsedResume>> violations = validator.validate(corrected);
        if (!violations.isEmpty()) {
            ConstraintViolation<ParsedResume> v = violations.iterator().next();
            throw new AppException(ErrorCode.VALIDATION_ERROR, v.getPropertyPath() + ": " + v.getMessage());
        }
        resume.correct(json.valueToTree(new ParsedResume(corrected.summary(), corrected.skills(), corrected.projects(),
                corrected.experience(), List.of())));
        return view(resumes.save(resume));
    }

    @Transactional
    public ResumeResponse activate(UUID userId, UUID id) {
        Resume resume = require(userId, id);
        resumes.deactivateAll(userId);
        resumes.flush();
        resume.setActive(true);
        return view(resumes.save(resume));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Resume resume = require(userId, id);
        boolean wasActive = resume.isActive();
        resumes.delete(resume);
        resumes.flush();
        if (wasActive) {
            resumes.findByUserIdOrderByCreatedAtDesc(userId).stream().findFirst().ifPresent(r -> {
                r.setActive(true);
                resumes.save(r);
            });
        }
    }

    /** The parsed profile for an interview: the given resume, or the active one. */
    @Transactional(readOnly = true)
    public ParsedForInterview parsedFor(UUID userId, UUID resumeId) {
        Resume resume = resumeId != null ? require(userId, resumeId)
                : resumes.findByUserIdAndActiveTrue(userId).orElseThrow(() -> new AppException(ErrorCode.RESUME_REQUIRED,
                        "Upload a resume to start a resume deep-dive"));
        if (resume.getStatus() != Resume.Status.PARSED || resume.getParsed() == null) {
            throw new AppException(ErrorCode.RESUME_REQUIRED, "Your resume is still being processed or failed to parse");
        }
        return new ParsedForInterview(resume.getId(), json.convertValue(resume.getParsed(), ParsedResume.class));
    }

    public record ParsedForInterview(UUID resumeId, ParsedResume parsed) {
    }

    /** Observer: deleting an account hard-deletes its resumes (files and extracted text). */
    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        resumes.findByUserIdOrderByCreatedAtDesc(event.userId()).forEach(resumes::delete);
    }

    private Resume require(UUID userId, UUID id) {
        return resumes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Resume not found"));
    }

    private ResumeResponse view(Resume r) {
        JsonNode parsed = r.getParsed();
        return new ResumeResponse(r.getId(), r.getOriginalFilename(), r.getSizeBytes(), r.getPageCount(),
                r.getStatus().name(), r.isActive(), parsed, r.getIntegrity(), r.getError(), r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
