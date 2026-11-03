# Backend image (FastAPI + Alembic). The frontend has its own image in frontend/.
#
# Stage 1 installs dependencies into a virtualenv; stage 2 copies only that
# virtualenv and the app code, so pip, build caches and the frontend toolchain
# never reach the runtime image.

FROM python:3.11-slim AS builder
ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
COPY requirements.txt .
# pip and setuptools are only needed to install, so they stay out of the runtime image.
RUN pip install -r requirements.txt \
 && pip uninstall -y pip setuptools

FROM python:3.11-slim AS runtime
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PATH="/opt/venv/bin:$PATH" \
    APP_ENV=production
RUN useradd --create-home --uid 10001 app
WORKDIR /app
COPY --from=builder /opt/venv /opt/venv
COPY alembic.ini main.py ./
COPY migrations ./migrations
COPY app ./app
COPY scripts/start.sh ./scripts/start.sh
USER app
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD python -c "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:%s/health' % os.environ.get('PORT','8000'), timeout=3)" || exit 1
CMD ["sh", "/app/scripts/start.sh"]
