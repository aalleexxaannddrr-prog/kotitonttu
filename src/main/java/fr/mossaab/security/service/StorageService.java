package fr.mossaab.security.service;

import fr.mossaab.security.entities.*;
import fr.mossaab.security.repository.FileDataRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для работы с хранилищем файлов.
 */
@Service
@AllArgsConstructor
public class StorageService {
    private static final String BASE_PATH = "C:\\Users\\Admin\\Desktop\\storage\\";
    private FileDataRepository fileDataRepository;

    /**
     * Загрузка PDF файла «взрыв-схема.pdf» для взрыв-схемы ExplosionDiagram.
     */
    public FileData uploadLocalPdfForExplosionDiagram(File localFile, ExplosionDiagram explosionDiagram) throws IOException {
        // Имя исходного файла (например, "взрыв-схема.pdf")
        String originalFilename = localFile.getName();
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex != -1) {
            extension = originalFilename.substring(dotIndex);
        }

        // Генерируем уникальное имя (UUID + .pdf)
        String name = UUID.randomUUID().toString() + extension;

        // Собираем FileData
        FileData.FileDataBuilder builder = FileData.builder();
        builder.name(name)
                .type("application/pdf")
                .filePath(BASE_PATH + "explosion_diagram_files\\" + name)
                .explosionDiagram(explosionDiagram); // привязка к ExplosionDiagram

        // Копируем файл в целевую папку
        Files.copy(
                localFile.toPath(),
                new File(builder.build().getFilePath()).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );

        // Сохраняем FileData в БД
        FileData fileData = builder.build();
        return fileDataRepository.save(fileData);
    }
    public FileData uploadLocalImageToFileSystemForSparePart(File localFile, SparePart sparePart) throws IOException {
        // 1. Генерируем новое имя файла
        String originalFilename = localFile.getName();
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex != -1) {
            extension = originalFilename.substring(dotIndex);
        }

        String name = UUID.randomUUID().toString() + extension;

        // 2. Собираем FileData
        FileData.FileDataBuilder builder = FileData.builder();
        builder
                .name(name)
                .type("image/png") // или получайте динамически MIME-тип, если нужно
                .filePath(BASE_PATH + "spare_part_files\\" + name)
                .sparePart(sparePart);

        // 3. Копируем файл в нужную папку
        Files.copy(
                localFile.toPath(),
                new File(builder.build().getFilePath()).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );

        // 4. Сохраняем FileData в БД
        FileData fileData = builder.build();
        return fileDataRepository.save(fileData);
    }


    // Универсальный метод для загрузки изображения с передачей объекта, с которым нужно связать файл
    public Object uploadImageToFileSystem(MultipartFile file, Object relatedEntity) throws IOException {
        String name;
        FileData.FileDataBuilder builder = FileData.builder();
        name = UUID.randomUUID().toString();
        System.out.println("Received related entity type: " + relatedEntity.getClass().getSimpleName());
        System.out.println("Related entity: " + relatedEntity);

        switch (relatedEntity.getClass().getSimpleName()) {
            case "ExplosionDiagram":
                ExplosionDiagram explosionDiagram = (ExplosionDiagram) relatedEntity;
                if (explosionDiagram.getFileData() != null) {
                    fileDataRepository.delete(explosionDiagram.getFileData());
                }
                builder.name(name + ".pdf");
                builder.type("image/png");
                builder.filePath(BASE_PATH + "explosion_diagram_files\\" + name + ".pdf");
                if (file != null && !file.isEmpty()) {
                    file.transferTo(new File(BASE_PATH + "explosion_diagram_files\\" + name + ".pdf"));
                }
                builder.explosionDiagram(explosionDiagram);
                break;
            case "Advantage":
                Advantage advantage = (Advantage) relatedEntity;
                if (advantage.getFileData() != null) {
                    fileDataRepository.delete(advantage.getFileData());
                }
                builder.name(name + ".png");
                builder.type("image/png");
                builder.filePath(BASE_PATH + "advantage_files\\" + name + ".png");
                if (file != null && !file.isEmpty()) {
                    file.transferTo(new File(BASE_PATH + "advantage_files\\" + name + ".png"));
                }
                builder.advantage(advantage);
                break;
            case "User":
                User user = (User) relatedEntity;
                if (user.getFileData() != null) {
                    fileDataRepository.delete(user.getFileData());
                }
                builder.name(name + ".png");
                builder.type("image/png");
                builder.filePath(BASE_PATH + "user_files\\" + name + ".png");
                if (file != null && !file.isEmpty()) {
                    file.transferTo(new File(BASE_PATH + "user_files\\" + name + ".png"));
                }
                builder.user(user);
                break;
            case "BonusRequest":
                BonusRequest bonusRequest = (BonusRequest) relatedEntity;
                builder.name(name + ".png");
                builder.type("image/png");
                builder.filePath(BASE_PATH + "bonus_request_files\\" + name + ".png");
                if (file != null && !file.isEmpty()) {
                    file.transferTo(new File(BASE_PATH + "bonus_request_files\\" + name + ".png"));
                }
                builder.bonusRequest(bonusRequest);
                break;
            case "DocumentVerificationRequest":
                DocumentVerificationRequest documentVerification = (DocumentVerificationRequest) relatedEntity;
                builder.name(name + ".png");
                builder.type("image/png");
                builder.filePath(BASE_PATH + "document_verification_files\\" + name + ".png");
                if (file != null && !file.isEmpty()) {
                    file.transferTo(new File(BASE_PATH + "document_verification_files\\" + name + ".png"));
                }
                builder.documentVerification(documentVerification);
                break;
            case "SparePart":
                SparePart sparePart = (SparePart) relatedEntity;
                builder.name(name + ".png");
                builder.type("image/png");
                builder.filePath(BASE_PATH + "spare_part_files\\" + name + ".png");
                if (file != null && !file.isEmpty()) {
                    file.transferTo(new File(BASE_PATH + "spare_part_files\\" + name + ".png"));
                }
                builder.sparePart(sparePart);
                break;
            case "BoilerSeriesPassport":
                builder.name(name + ".pdf");
                builder.type("application/pdf");
                builder.filePath(BASE_PATH + "boiler_series_passport_files\\" + name + ".pdf");
                if (file != null && !file.isEmpty()) {
                    file.transferTo(new File(BASE_PATH + "boiler_series_passport_files\\" + name + ".pdf"));
                }
                if (relatedEntity instanceof BoilerSeriesPassport) {
                    BoilerSeriesPassport boilerSeriesPassport = (BoilerSeriesPassport) relatedEntity;
                    builder.boilerSeriesPassport(boilerSeriesPassport);
                }
                break;
            case "Series":
                Series series = (Series) relatedEntity;
                builder.name(name + ".png");
                builder.type("image/png");
                builder.filePath(BASE_PATH + "series_files\\" + name + ".png");
                if (file != null && !file.isEmpty()) {
                    file.transferTo(new File(BASE_PATH + "series_files\\" + name + ".png"));
                }
                builder.series(series);
                break;
            default:
                throw new IllegalArgumentException("Unsupported related entity type: " + relatedEntity.getClass().getSimpleName());
        }

        FileData fileData = builder.build();
        fileData = fileDataRepository.save(fileData);
        return fileData;
    }

    public FileData uploadLocalImageToFileSystem(File localFile, Series series) throws IOException {
        // В качестве примера — можно взять имя файла как UUID + расширение
        String originalFilename = localFile.getName();
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex != -1) {
            extension = originalFilename.substring(dotIndex);
        }

        // Генерируем новое имя
        String name = UUID.randomUUID().toString() + extension;

        // Собираем FileData
        FileData.FileDataBuilder builder = FileData.builder();
        builder.name(name)
                .type("image/png")  // или определять динамически, если нужно
                .filePath(BASE_PATH + "series_files\\" + name)
                .series(series);

        // Фактически копируем файл в нужную папку
        Files.copy(localFile.toPath(),
                new File(builder.build().getFilePath()).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Сохраняем в БД
        FileData fileData = builder.build();
        return fileDataRepository.save(fileData);
    }


    public Object uploadImageToFileSystemWithName(MultipartFile file, String name) throws IOException {
        FileData.FileDataBuilder builder = FileData.builder();
        builder.name(name + ".png");
        builder.type("image/png");
        builder.filePath(BASE_PATH + "explosion_diagram_files\\" + name + ".png");
        if (file != null && !file.isEmpty()) {
            file.transferTo(new File(BASE_PATH + "explosion_diagram_files\\" + name + ".png"));
        }
        FileData fileData = builder.build();
        fileData = fileDataRepository.save(fileData);
        return fileData;
    }

    public byte[] downloadImageFromFileSystem(String fileName) throws IOException {
        Optional<FileData> fileData = fileDataRepository.findByName(fileName);
        String filePath = fileData.get().getFilePath();
        return Files.readAllBytes(new File(filePath).toPath());
    }

    public FileData uploadLocalPdfForBoilerSeriesPassport(File localFile, BoilerSeriesPassport passport) throws IOException {
        // Имя исходного файла
        String originalFilename = localFile.getName();
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex != -1) {
            extension = originalFilename.substring(dotIndex);
        }

        // Генерируем новое имя
        String name = UUID.randomUUID().toString() + extension;

        // Собираем FileData
        FileData.FileDataBuilder builder = FileData.builder();
        builder.name(name)
                .type("application/pdf")
                .filePath(BASE_PATH + "boiler_series_passport_files\\" + name)
                .boilerSeriesPassport(passport); // связка с BoilerSeriesPassport

        // Копируем файл в нужную папку
        Files.copy(localFile.toPath(),
                new File(builder.build().getFilePath()).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Сохраняем FileData в БД
        FileData fileData = builder.build();
        fileData = fileDataRepository.save(fileData);

        // Двухсторонняя связь (если нужно явно):
        // passport.setFile(fileData);

        return fileData;
    }


    public FileData uploadLocalImageToFileSystemForAdvantage(File localFile, Advantage advantage) throws IOException {
        // Имя файла
        String originalFilename = localFile.getName();
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex != -1) {
            extension = originalFilename.substring(dotIndex);
        }

        // Генерируем новое имя (например, UUID + .png)
        String name = UUID.randomUUID().toString() + extension;

        // Собираем FileData
        FileData.FileDataBuilder builder = FileData.builder();
        builder.name(name)
                .type("image/png")
                .filePath(BASE_PATH + "advantage_files\\" + name)
                .advantage(advantage);  // Привязываем к Advantage

        // Копируем файл в нужную папку
        Files.copy(localFile.toPath(),
                new File(builder.build().getFilePath()).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Сохраняем в БД
        FileData fileData = builder.build();
        return fileDataRepository.save(fileData);
    }

}