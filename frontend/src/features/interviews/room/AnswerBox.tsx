import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { buttonClass } from '../../../components/ui/styles';
import { recognitionConstructor, type Recognition } from './voice';

export const MAX_ANSWER_LENGTH = 8000;

/**
 * The candidate's answer composer. Ctrl/Cmd+Enter submits. With voice enabled (and a browser that supports
 * SpeechRecognition), a mic button dictates into the box; the answer is then sent with inputMode VOICE.
 */
export function AnswerBox({
  value,
  onChange,
  onSubmit,
  disabled,
  busy,
  disabledReason,
  voiceEnabled,
  onPaste,
  onDictated,
}: {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  disabled: boolean;
  busy: boolean;
  disabledReason?: string | undefined;
  voiceEnabled: boolean;
  onPaste: (size: number) => void;
  onDictated: () => void;
}) {
  const [listening, setListening] = useState(false);
  const [voiceError, setVoiceError] = useState<string | null>(null);
  const recognition = useRef<Recognition | null>(null);
  const valueRef = useRef(value);
  valueRef.current = value;
  const Ctor = voiceEnabled ? recognitionConstructor() : null;

  useEffect(() => () => recognition.current?.stop(), []);

  function toggleMic() {
    if (!Ctor) return;
    if (listening) {
      recognition.current?.stop();
      return;
    }
    const rec = new Ctor();
    rec.continuous = true;
    rec.interimResults = false;
    rec.lang = navigator.language || 'en-IN';
    rec.onresult = (event) => {
      let text = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i];
        if (result?.isFinal) text += result[0].transcript;
      }
      if (text.trim()) {
        const current = valueRef.current;
        onChange(`${current}${current && !current.endsWith(' ') ? ' ' : ''}${text.trim()}`.slice(0, MAX_ANSWER_LENGTH));
        onDictated();
      }
    };
    rec.onerror = (event) => {
      setVoiceError(event.error === 'not-allowed' ? 'Microphone permission was denied.' : `Voice input stopped (${event.error}).`);
    };
    rec.onend = () => setListening(false);
    recognition.current = rec;
    setVoiceError(null);
    try {
      rec.start();
      setListening(true);
    } catch {
      setVoiceError('Could not start voice input.');
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!disabled && value.trim()) {
      recognition.current?.stop();
      onSubmit();
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      if (!disabled && value.trim()) onSubmit();
    }
  }

  const remaining = MAX_ANSWER_LENGTH - value.length;

  return (
    <form onSubmit={handleSubmit} className="border-t-3 border-ink p-4">
      <label htmlFor="answer" className="nb-label">
        Your answer
      </label>
      <textarea
        id="answer"
        className="nb-input min-h-28 resize-y"
        value={value}
        maxLength={MAX_ANSWER_LENGTH}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        onPaste={(e) => onPaste(e.clipboardData.getData('text').length)}
        aria-describedby="answer-hint"
        placeholder="Think out loud: explain your approach, trade-offs and complexity…"
      />
      <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
        <p id="answer-hint" className="text-xs font-medium text-muted">
          {disabled && disabledReason ? disabledReason : 'Ctrl/⌘ + Enter to send'} · {remaining} characters left
        </p>
        <div className="flex gap-2">
          {Ctor && (
            <button
              type="button"
              onClick={toggleMic}
              aria-pressed={listening}
              className={`${listening ? buttonClass.danger : buttonClass.secondary} nb-btn-sm`}
            >
              {listening ? '■ Stop dictation' : '🎙 Dictate'}
            </button>
          )}
          <button type="submit" className={`${buttonClass.primary} nb-btn-sm`} disabled={disabled || !value.trim()}>
            {busy ? 'Sending…' : 'Send answer'}
          </button>
        </div>
      </div>
      {voiceError && (
        <p role="alert" className="mt-2 inline-block bg-nb-red px-1.5 text-sm font-bold text-[#0a0a0a]">
          {voiceError}
        </p>
      )}
      {voiceEnabled && !recognitionConstructor() && (
        <p className="mt-2 text-xs font-medium text-muted">Voice dictation isn't supported in this browser; type your answer instead.</p>
      )}
    </form>
  );
}
