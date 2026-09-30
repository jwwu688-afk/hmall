FROM python:3.12-slim

WORKDIR /app
COPY pyproject.toml ./
COPY app ./app
RUN pip install --no-cache-dir .
RUN mkdir -p /app/data && useradd -r -u 10001 agent && chown -R agent:agent /app/data
USER agent
EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
