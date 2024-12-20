package fr.mossaab.security.service;

import fr.mossaab.security.dto.BarcodeSummaryDto;
import fr.mossaab.security.dto.request.BarcodeTypeDto;
import fr.mossaab.security.dto.request.BarcodeTypeUpdateDto;
import fr.mossaab.security.dto.request.BarcodeUpdateDto;
import fr.mossaab.security.entities.*;
import fr.mossaab.security.enums.RequestStatus;
import fr.mossaab.security.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class BonusService {
    private StorageService boilerPurchasePhotoService;
    private  RefreshTokenRepository refreshTokenRepository;
    private UserRepository userRepository;
    private BarcodeTypeRepository barcodeTypeRepository;
    private BonusRequestRepository bonusRequestRepository;
    private BarcodeRepository barcodeRepository;
    private DocumentVerificationRepository documentVerificationRepository;
    public ResponseEntity<Void> deleteBarcodeType(Long id) {
        // Найти сущность BarcodeType по id
        BarcodeType barcodeType = barcodeTypeRepository.findById(id).orElse(null);

        if (barcodeType == null) {
            return ResponseEntity.notFound().build();
        }

        // Удаление сущности BarcodeType
        barcodeTypeRepository.delete(barcodeType);

        return ResponseEntity.noContent().build();
    }
    public String uploadPhotos(List<MultipartFile> photos, String cookie, Long barcodeCode) throws IOException {
        // Получение текущего пользователя по cookie
        var user = refreshTokenRepository.findByToken(cookie).orElse(null).getUser();
        if (user == null) {
            throw new SecurityException("Invalid or expired token.");
        }

        // Проверяем существование штрих-кода
        var barcode = barcodeRepository.findByCode(barcodeCode).orElse(null);
        if (barcode == null) {
            throw new IllegalArgumentException("Barcode not found.");
        }

        // Проверяем, был ли этот штрих-код уже использован
        if (barcode.isUsed()) {
            throw new IllegalArgumentException("This barcode has already been successfully activated.");
        }

        // Получаем все запросы пользователя
        List<BonusRequest> userRequests = bonusRequestRepository.findAll().stream()
                .filter(request -> request.getUser().equals(user))
                .toList();

        // Проверяем, существует ли запрос с данным штрих-кодом
        for (BonusRequest request : userRequests) {
            if (request.getBarcode().equals(barcode)) {
                if (request.getStatus() == RequestStatus.PENDING) {
                    throw new IllegalStateException("A request for this barcode is already pending approval.");
                }
                if (request.getStatus() == RequestStatus.REJECTED) {
                    bonusRequestRepository.delete(request);
                    break; // Прекращаем проверку, так как удаляем запрос
                }
            }
        }

        // Создание нового BonusRequest
        BonusRequest bonusRequest = BonusRequest.builder()
                .status(RequestStatus.PENDING)
                .user(user)
                .barcode(barcode)
                .requestDate(LocalDateTime.now())
                .build();

        // Сначала сохраняем BonusRequest
        bonusRequest = bonusRequestRepository.save(bonusRequest);

        List<FileData> boilerPurchasePhotos = new ArrayList<>();

        // Проходим по списку фотографий и сохраняем каждую
        for (MultipartFile photo : photos) {
            FileData savedPhoto = (FileData) boilerPurchasePhotoService.uploadImageToFileSystem(photo, bonusRequest);
            boilerPurchasePhotos.add(savedPhoto);
        }

        // Связываем фотографии с BonusRequest
        bonusRequest.setFiles(boilerPurchasePhotos);

        // Сохраняем обновленный BonusRequest с фотографиями
        bonusRequestRepository.save(bonusRequest);

        return "Photos uploaded successfully and BonusRequest created.";
    }

    public String uploadPhotosPassport(List<MultipartFile> photos, String cookie) throws IOException {
        // Получение текущего пользователя по cookie
        var user = refreshTokenRepository.findByToken(cookie).orElse(null).getUser();
        if (user == null) {
            throw new SecurityException("Invalid or expired token.");
        }

        DocumentVerificationRequest documentVerification = DocumentVerificationRequest.builder()
                .user(user)
                .status(RequestStatus.PENDING)
                .requestDate(LocalDateTime.now())
                .build();

        // Сначала сохраняем BonusRequest
        documentVerification = documentVerificationRepository.save(documentVerification);

        List<FileData> passportPhotos = new ArrayList<>();

        // Проходим по списку фотографий и сохраняем каждую
        for (MultipartFile photo : photos) {
            FileData savedPhoto = (FileData) boilerPurchasePhotoService.uploadImageToFileSystem(photo, documentVerification);
            passportPhotos.add(savedPhoto);
        }


        // Связываем фотографии с BonusRequest
        documentVerification.setFileDataList(passportPhotos);

        // Сохраняем обновленный BonusRequest с фотографиями
        documentVerificationRepository.save(documentVerification);

        return "Photos uploaded successfully and document verification created.";
    }

    public List<BarcodeTypeDto> getAllBarcodeTypes() {
        List<BarcodeType> barcodeTypes = barcodeTypeRepository.findAll();
        List<BarcodeTypeDto> barcodeTypeDtos = new ArrayList<>();

        for (BarcodeType barcodeType : barcodeTypes) {
            barcodeTypeDtos.add(new BarcodeTypeDto(
                    barcodeType.getId(),
                    barcodeType.getPoints(),
                    barcodeType.getType(),
                    barcodeType.getSubtype()
            ));
        }

        return barcodeTypeDtos;
    }
    public ResponseEntity<Void> deleteBarcode(Long id) {
        // Найти сущность Barcode по id
        Barcode barcode = barcodeRepository.findById(id).orElse(null);

        if (barcode == null) {
            return ResponseEntity.notFound().build();
        }

        // Удаление сущности Barcode
        barcodeRepository.delete(barcode);

        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<String> updateBalance(String email, int amount) {
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setBalance(user.getBalance() + amount);
            userRepository.save(user);
            return ResponseEntity.ok("Balance updated successfully.");
        } else {
            return ResponseEntity.badRequest().body("User not found.");
        }
    }


    public ResponseEntity<List<Map<String, Object>>> getBonusRequests(RequestStatus requestStatus) {
        // Получаем всех пользователей
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        // Проходим по каждому пользователю
        for (User user : users) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("email", user.getEmail());

            List<Map<String, Object>> bonusRequestList = new ArrayList<>();

            // Получаем все бонусные запросы пользователя
            List<BonusRequest> bonusRequests = new ArrayList<>(user.getBonusRequests());

            // Фильтруем по статусу и сортируем по requestDate от новых к старым
            List<BonusRequest> filteredBonusRequests = bonusRequests.stream()
                    .filter(br -> br.getStatus() == requestStatus)
                    .sorted((br1, br2) -> {
                        if (br1.getRequestDate() == null && br2.getRequestDate() == null) {
                            return 0;
                        } else if (br1.getRequestDate() == null) {
                            return 1;
                        } else if (br2.getRequestDate() == null) {
                            return -1;
                        } else {
                            return br2.getRequestDate().compareTo(br1.getRequestDate());
                        }
                    })
                    .collect(Collectors.toList());

            // Проходим по отсортированным бонусным запросам
            for (BonusRequest bonusRequest : filteredBonusRequests) {
                Map<String, Object> bonusRequestMap = new HashMap<>();
                bonusRequestMap.put("bonusRequestId", bonusRequest.getId());

                // Добавляем сообщение об отказе, если статус - ОТКЛОНЕН
                if (requestStatus == RequestStatus.REJECTED) {
                    bonusRequestMap.put("rejectionMessage", bonusRequest.getRejectionMessage());
                }

                // Добавляем статус бонусного запроса
                bonusRequestMap.put("status", bonusRequest.getStatus().name());
                bonusRequestMap.put("requestDate", bonusRequest.getRequestDate());

                // Добавляем ссылки на фотографии
                List<String> photoNames = bonusRequest.getFiles().stream()
                        .map(photo -> "http://31.129.102.70:8080/user/fileSystem/" + photo.getName())
                        .collect(Collectors.toList());
                bonusRequestMap.put("photos", photoNames);

                // Добавляем данные о barcode и barcodeType
                Barcode barcode = bonusRequest.getBarcode();
                bonusRequestMap.put("barcode", barcode.getCode());
                if (barcode != null) {
                    BarcodeType barcodeType = barcode.getBarcodeType();
                    if (barcodeType != null) {
                        bonusRequestMap.put("points", barcodeType.getPoints());
                        bonusRequestMap.put("type", barcodeType.getType());
                        bonusRequestMap.put("subtype", barcodeType.getSubtype());
                    }
                }

                bonusRequestList.add(bonusRequestMap);
            }

            userMap.put("bonusRequests", bonusRequestList);
            result.add(userMap);
        }

        return ResponseEntity.ok(result);
    }


    public List<Map<String, Object>> getUserBonusRequests(String email) {
        // Находим пользователя по email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь с email " + email + " не найден"));

        List<Map<String, Object>> bonusRequestList = new ArrayList<>();

        // Получаем все бонусные запросы пользователя
        List<BonusRequest> bonusRequests = new ArrayList<>(user.getBonusRequests());

        // Проходим по всем бонусным запросам
        for (BonusRequest bonusRequest : bonusRequests) {
            Map<String, Object> bonusRequestMap = new HashMap<>();
            bonusRequestMap.put("bonusRequestId", bonusRequest.getId());
            bonusRequestMap.put("status", bonusRequest.getStatus().name());
            bonusRequestMap.put("requestDate", bonusRequest.getRequestDate());

            // Добавляем сообщения для отклоненных заявок
            if (bonusRequest.getStatus() == RequestStatus.REJECTED) {
                bonusRequestMap.put("rejectionMessage", bonusRequest.getRejectionMessage());
            }

            List<String> photoUrls = new ArrayList<>();
            // Получаем ссылки на фотографии
            for (FileData photo : bonusRequest.getFiles()) {
                photoUrls.add("http://31.129.102.70:8080/user/fileSystem/" + photo.getName());
            }

            bonusRequestMap.put("photos", photoUrls);
            bonusRequestList.add(bonusRequestMap);
        }

        return bonusRequestList;
    }

    public List<Map<String, Object>> getUserBonusRequestsByStatus(String email, RequestStatus status) {
        // Находим пользователя по email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь с email " + email + " не найден"));

        // Фильтруем запросы пользователя по статусу и сортируем их
        List<BonusRequest> filteredBonusRequests = user.getBonusRequests().stream()
                .filter(bonusRequest -> bonusRequest.getStatus() == status)
                .sorted((br1, br2) -> {
                    if (br1.getRequestDate() == null && br2.getRequestDate() == null) {
                        return 0;
                    } else if (br1.getRequestDate() == null) {
                        return 1;
                    } else if (br2.getRequestDate() == null) {
                        return -1;
                    } else {
                        return br2.getRequestDate().compareTo(br1.getRequestDate());
                    }
                })
                .collect(Collectors.toList());

        // Формируем список результатов
        List<Map<String, Object>> bonusRequestList = new ArrayList<>();
        for (BonusRequest bonusRequest : filteredBonusRequests) {
            Map<String, Object> bonusRequestMap = new HashMap<>();
            bonusRequestMap.put("bonusRequestId", bonusRequest.getId());
            bonusRequestMap.put("status", bonusRequest.getStatus().name());
            bonusRequestMap.put("requestDate", bonusRequest.getRequestDate());

            // Добавляем сообщение об отклонении, если статус - ОТКЛОНЕН
            if (bonusRequest.getStatus() == RequestStatus.REJECTED) {
                bonusRequestMap.put("rejectionMessage", bonusRequest.getRejectionMessage());
            }

            // Добавляем ссылки на фотографии
            List<String> photoUrls = bonusRequest.getFiles().stream()
                    .map(photo -> "http://31.129.102.70:8080/user/fileSystem/" + photo.getName())
                    .collect(Collectors.toList());
            bonusRequestMap.put("photos", photoUrls);

            // Добавляем данные о barcode и barcodeType
            Barcode barcode = bonusRequest.getBarcode();
            bonusRequestMap.put("barcode", barcode.getCode());
            if (barcode != null) {
                BarcodeType barcodeType = barcode.getBarcodeType();
                if (barcodeType != null) {
                    bonusRequestMap.put("points", barcodeType.getPoints());
                    bonusRequestMap.put("type", barcodeType.getType());
                    bonusRequestMap.put("subtype", barcodeType.getSubtype());
                }
            }

            bonusRequestList.add(bonusRequestMap);
        }

        return bonusRequestList;
    }


    public ResponseEntity<List<Map<String, Object>>> getDocumentVerifications(RequestStatus requestStatus) {
        // Получаем всех пользователей
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        // Проходим по каждому пользователю
        for (User user : users) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("email", user.getEmail());

            List<Map<String, Object>> documentVerificationList = new ArrayList<>();

            // Получаем все бонусные запросы пользователя
            List<DocumentVerificationRequest> documentVerifications = new ArrayList<>(user.getDocumentVerifications());

            // Фильтруем по статусу и сортируем по requestSentDate от новых к старым
            List<DocumentVerificationRequest> chosenDocumentVerification = documentVerifications.stream()
                    .filter(br -> br.getStatus() == requestStatus)
                    .sorted((br1, br2) -> {
                        if (br1.getRequestDate() == null && br2.getRequestDate() == null) {
                            return 0;
                        } else if (br1.getRequestDate() == null) {
                            return 1;
                        } else if (br2.getRequestDate() == null) {
                            return -1;
                        } else {
                            return br2.getRequestDate().compareTo(br1.getRequestDate());
                        }
                    })
                    .collect(Collectors.toList());


            for (DocumentVerificationRequest documentVerificationfor : chosenDocumentVerification) {
                Map<String, Object> documentVerificationMap = new HashMap<>();
                documentVerificationMap.put("documentVerificationId", documentVerificationfor.getId());
                if(requestStatus == RequestStatus.REJECTED) {
                    documentVerificationMap.put("rejectionMessage", documentVerificationfor.getRejectionMessage());
                }

                // Добавляем статус бонусного запроса
                documentVerificationMap.put("status", documentVerificationfor.getStatus().name());
                documentVerificationMap.put("requestDate", documentVerificationfor.getRequestDate());

                List<String> photoNames = new ArrayList<>();
                // Получаем все фотографии покупки котла для этого бонусного запроса
                for (FileData photo : documentVerificationfor.getFileDataList()) {
                    photoNames.add("http://31.129.102.70:8080/user/fileSystem/" + photo.getName());
                }

                documentVerificationMap.put("photos", photoNames);
                documentVerificationList.add(documentVerificationMap);
            }

            userMap.put("documentVerifications", documentVerificationList);
            result.add(userMap);
        }



        // Возвращаем результат в виде JSON
        return ResponseEntity.ok(result);
    }

    public ResponseEntity<String> updateBonusRequestStatus(Long requestId, RequestStatus status, String rejectionMessage) {
        // Поиск BonusRequest по ID
        var bonusRequest = bonusRequestRepository.findById(requestId).orElse(null);
        if (bonusRequest == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("BonusRequest not found.");
        }

        // Установка статуса и сообщения об отказе, если статус REJECTED
        bonusRequest.setStatus(status);
        bonusRequest.setResponseDate(LocalDateTime.now());
        if (status == RequestStatus.REJECTED && rejectionMessage != null) {
            bonusRequest.setRejectionMessage(rejectionMessage);
        }
        if (status == RequestStatus.APPROVED) {
            bonusRequest.getBarcode().setUsed(true);
            bonusRequest.getUser().setBalance(bonusRequest.getUser().getBalance() + bonusRequest.getBarcode().getBarcodeType().getPoints());
        }

        // Сохранение обновленного BonusRequest
        bonusRequestRepository.save(bonusRequest);

        return ResponseEntity.ok("BonusRequest status updated successfully.");
    }

    public ResponseEntity<String> updatePassportStatus(Long documentVerificationId, RequestStatus status, String rejectionMessage) {
        // Поиск BonusRequest по ID
        var documentVerification = documentVerificationRepository.findById(documentVerificationId).orElse(null);
        if (documentVerification == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Document verification not found.");
        }

        // Установка статуса и сообщения об отказе, если статус REJECTED
        documentVerification.setStatus(status);
        documentVerification.setResponseDate(LocalDateTime.now());
        if (status == RequestStatus.REJECTED && rejectionMessage != null) {
            documentVerification.setRejectionMessage(rejectionMessage);
        }

        // Сохранение обновленного BonusRequest
        documentVerificationRepository.save(documentVerification);

        return ResponseEntity.ok("DocumentVerificationRequest status updated successfully.");
    }

    public ResponseEntity<BarcodeType> addBarcodeType(int points, String type, String subtype) {
        BarcodeType barcodeType = new BarcodeType();
        barcodeType.setPoints(points);
        barcodeType.setType(type);
        barcodeType.setSubtype(subtype);

        BarcodeType savedBarcodeType = barcodeTypeRepository.save(barcodeType);

        // Возвращаем ResponseEntity с объектом и статусом 201 Created
        return new ResponseEntity<>(savedBarcodeType, HttpStatus.CREATED);
    }

    public ResponseEntity<List<BarcodeSummaryDto>> getAllBarcodesSummary() {
        List<Barcode> barcodes = barcodeRepository.findAll();
        List<BarcodeSummaryDto> barcodeSummaryDtos = new ArrayList<>();

        // Используем цикл for для обработки списка
        for (Barcode barcode : barcodes) {
            BarcodeSummaryDto summaryDto = new BarcodeSummaryDto(barcode.getId(), barcode.getCode(), barcode.isUsed());
            barcodeSummaryDtos.add(summaryDto);
        }

        return ResponseEntity.ok(barcodeSummaryDtos);
    }
    public ResponseEntity<Barcode> updateBarcode(Long id, BarcodeUpdateDto updateDto) {
        Optional<Barcode> existingBarcode = barcodeRepository.findById(id);
        if (existingBarcode.isPresent()) {
            Barcode barcode = existingBarcode.get();
            barcode.setCode(updateDto.getCode());
            barcode.setUsed(updateDto.isUsed());
            return ResponseEntity.ok(barcodeRepository.save(barcode));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    public ResponseEntity<BarcodeType> updateBarcodeType(Long id, BarcodeTypeUpdateDto updateDto) {
        Optional<BarcodeType> existingBarcodeType = barcodeTypeRepository.findById(id);
        if (existingBarcodeType.isPresent()) {
            BarcodeType barcodeType = existingBarcodeType.get();
            barcodeType.setPoints(updateDto.getPoints());
            barcodeType.setType(updateDto.getType());
            barcodeType.setSubtype(updateDto.getSubtype());
            return ResponseEntity.ok(barcodeTypeRepository.save(barcodeType));
        } else {
            return ResponseEntity.notFound().build();
        }
    }


}
