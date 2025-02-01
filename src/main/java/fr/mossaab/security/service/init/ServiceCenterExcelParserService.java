package fr.mossaab.security.service.init;

import fr.mossaab.security.entities.ServiceCenter;
import fr.mossaab.security.repository.ServiceCenterRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;

@Service
@Transactional
public class ServiceCenterExcelParserService {

    // Путь к Excel-файлу
    private static final String EXCEL_FILE_PATH = "/app/uploads/Реестр сервисных центров.xlsx";

    private final ServiceCenterRepository serviceCenterRepository;

    public ServiceCenterExcelParserService(ServiceCenterRepository serviceCenterRepository) {
        this.serviceCenterRepository = serviceCenterRepository;
    }

    public void parseAndSaveServiceCenters() {
        // DataFormatter помогает корректно считывать ячейки в виде строки
        DataFormatter dataFormatter = new DataFormatter();

        try (FileInputStream fis = new FileInputStream(EXCEL_FILE_PATH);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Предполагаем, что данные на первом листе
            Sheet sheet = workbook.getSheetAt(0);

            // Начинаем парсить со второй строки (индекс = 1),
            // пропуская первую (индекс = 0) с заголовками
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                // Пропускаем пустые строки
                if (row == null) continue;

                // Считываем данные из ячеек, используя dataFormatter
                String title             = dataFormatter.formatCellValue(row.getCell(1)).trim(); // Название
                String city              = dataFormatter.formatCellValue(row.getCell(2)).trim(); // Город
                String address           = dataFormatter.formatCellValue(row.getCell(3)).trim(); // Адрес
                String phone             = dataFormatter.formatCellValue(row.getCell(4)).trim(); // Телефон
                String servicedEquipment = dataFormatter.formatCellValue(row.getCell(5)).trim(); // Обслуживаемое оборудование
                String latitudeString    = dataFormatter.formatCellValue(row.getCell(6)).trim(); // Широта
                String longitudeString   = dataFormatter.formatCellValue(row.getCell(7)).trim(); // Долгота

                // Если вся строка пустая — пропускаем
                if (title.isEmpty() && city.isEmpty() && address.isEmpty() && phone.isEmpty()
                        && servicedEquipment.isEmpty() && latitudeString.isEmpty() && longitudeString.isEmpty()) {
                    continue;
                }

                // Преобразуем широту/долготу в число (при необходимости)
                double latitude = parseDouble(latitudeString);
                double longitude = parseDouble(longitudeString);

                // Создаём объект сущности
                ServiceCenter serviceCenter = ServiceCenter.builder()
                        .title(title)
                        .city(city)
                        .address(address)
                        .phone(phone)
                        .servicedEquipment(servicedEquipment)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build();

                // Сохраняем в БД
                serviceCenterRepository.save(serviceCenter);
            }

        } catch (IOException e) {
            // Здесь можно использовать ваш Logger
            System.err.println("Ошибка при чтении файла: " + EXCEL_FILE_PATH + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Преобразуем строку в double. При ошибке — возвращаем 0.0
     */
    private double parseDouble(String value) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }
        // На случай, если кто-то использует запятую как десятичный разделитель
        value = value.replace(',', '.');
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
