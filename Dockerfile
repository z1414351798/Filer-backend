FROM eclipse-temurin:17-jre-jammy

# Install system tools: LibreOffice (for DOCX/XLSX/PPTX→PDF), Tesseract (for OCR), FFmpeg (for video/audio)
RUN apt-get update && apt-get install -y --no-install-recommends \
        libreoffice-writer \
        libreoffice-calc \
        libreoffice-impress \
        tesseract-ocr \
        tesseract-ocr-eng \
        tesseract-ocr-chi-sim \
        ffmpeg \
        fonts-liberation \
        fonts-noto-cjk \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata
ENV LIBREOFFICE_PATH=libreoffice
ENV FFMPEG_PATH=/usr/bin/ffmpeg

WORKDIR /app
COPY target/filer-backend-*.jar app.jar

RUN mkdir -p /tmp/filer/uploads /tmp/filer/outputs

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
