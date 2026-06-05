FROM python:3.12-slim
WORKDIR /app
COPY main.py .
COPY config config/
RUN pip install --no-cache-dir fastapi uvicorn
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "10000"]
