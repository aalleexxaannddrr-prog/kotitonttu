package fr.mossaab.security.service.init;

import fr.mossaab.security.entities.*;
import fr.mossaab.security.enums.CategoryOfAdvantage;
import fr.mossaab.security.repository.*;
import fr.mossaab.security.service.StorageService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class ParserService {

    private static final String FOLDER_PATH = "/var/www/system_initialization";
    private static final Logger logger = LoggerFactory.getLogger(ParserService.class);

    @Autowired
    private TypeRepository typeRepository;
    @Autowired
    private CharacteristicRepository characteristicRepository;
    @Autowired
    private SeriesRepository seriesRepository;
    @Autowired
    private KindRepository kindRepository;
    @Autowired
    private UnitRepository unitRepository;
    @Autowired
    private BoilerRepository boilerRepository;
    @Autowired
    private ValueRepository valueRepository;
    // Репозиторий для сохранения Advantage
    @Autowired
    private AdvantageRepository advantageRepository;
    // Репозиторий для BoilerSeriesPassport
    @Autowired
    private BoilerSeriesPassportRepository boilerSeriesPassportRepository;

    // Сервис для загрузки файлов (Series, Advantage, BoilerSeriesPassport и т.д.)
    @Autowired
    private StorageService storageService;

    public void parseAndSaveTypes() {
        List<Type> types = parseTypes();
        if (!types.isEmpty()) {
            typeRepository.saveAll(types);
            System.out.println("Сохранено " + types.size() + " типов в базу данных.");
        } else {
            System.out.println("Не найдено ни одной папки для создания типов.");
        }
    }

    private List<Type> parseTypes() {
        List<Type> types = new ArrayList<>();
        File folder = new File(FOLDER_PATH);

        if (folder.exists() && folder.isDirectory()) {
            File[] directories = folder.listFiles(File::isDirectory);
            if (directories != null) {
                for (File dir : directories) {
                    String folderName = dir.getName();
                    String title = extractTitle(folderName);
                    String description = extractDescription(folderName);

                    Type type = Type.builder()
                            .title(title)
                            .description(description != null ? description : "Описание отсутствует")
                            .build();

                    List<Kind> kinds = parseKinds(dir, type);
                    type.setKinds(kinds);

                    types.add(type);
                }
            }
        } else {
            System.out.println("Указанный путь не существует или не является директорией: " + FOLDER_PATH);
        }

        return types;
    }

    private List<Kind> parseKinds(File typeDir, Type type) {
        List<Kind> kinds = new ArrayList<>();
        File[] subDirectories = typeDir.listFiles(File::isDirectory);
        if (subDirectories != null) {
            for (File subDir : subDirectories) {
                String kindTitle = subDir.getName().trim();

                Kind kind = Kind.builder()
                        .title(kindTitle)
                        .type(type)
                        .build();

                // Сразу сохраняем Kind, чтобы иметь корректный ID
                kind = kindRepository.save(kind);

                List<Series> seriesList = parseSeries(subDir, kind);
                kind.setSeries(seriesList);

                kinds.add(kind);
            }
        }
        return kinds;
    }

    private List<Series> parseSeries(File kindDir, Kind kind) {
        List<Series> seriesList = new ArrayList<>();
        File[] seriesDirectories = kindDir.listFiles(File::isDirectory);

        if (seriesDirectories != null) {
            for (File seriesDir : seriesDirectories) {
                String seriesName = seriesDir.getName().trim();

                Series series = extractSeriesData(seriesName);
                if (series != null) {
                    series.setKind(kind);

                    // Ищем Excel-файл, используя / в пути
                    String excelFilePath = seriesDir.getPath() + "/" + seriesName + ".xlsx";
                    File excelFile = new File(excelFilePath);
                    if (excelFile.exists()) {
                        List<Characteristic> characteristics = parseCharacteristicsFromExcel(excelFile);
                        series.setCharacteristics(characteristics);

                        // сначала сохраняем серию в БД, чтобы при дальнейшей работе у неё был ID
                        series = seriesRepository.save(series);

                        // Парсим бойлеры и их значения из Excel
                        parseAndSaveBoilersWithValues(excelFile, series, characteristics);
                    } else {
                        System.out.println("Excel file not found for series: " + seriesName);
                        // всё равно сохраняем серию, даже без Excel
                        series = seriesRepository.save(series);
                    }

                    // Ищем PNG-файл с тем же именем, что и у серии (старый функционал)
                    File pngFile = new File(seriesDir.getPath(), seriesName + ".png");
                    if (pngFile.exists()) {
                        try {
                            storageService.uploadLocalImageToFileSystem(pngFile, series);
                        } catch (IOException e) {
                            logger.error("Ошибка при загрузке PNG файла для серии: {}", seriesName, e);
                        }
                    }

                    // Парсим Advantage (три папки) для текущей серии
                    parseAdvantages(seriesDir, series);

                    // NEW: Парсим BoilerSeriesPassport (PDF) для текущей серии
                    parseBoilerSeriesPassport(seriesDir, series);

                    seriesList.add(series);
                }
            }
        }
        return seriesList;
    }

    /**
     * Парсим PDF-файл, соответствующий текущей серии (если он есть).
     * Пример: "QM4-24.pdf" для серии "QM4-24".
     */
    private void parseBoilerSeriesPassport(File seriesDir, Series series) {
        String seriesName = series.getPrefix() + series.getStartRange() + "-" + series.getEndRange();
        if (series.getSuffix() != null && !series.getSuffix().isEmpty()) {
            seriesName += series.getSuffix(); // формируем полное имя (например, "QM4-24")
        }

        // Ищем PDF-файл
        File pdfFile = new File(seriesDir, seriesName + ".pdf");
        if (!pdfFile.exists()) {
            // Нет PDF-файла = нет BoilerSeriesPassport
            logger.info("PDF-файл {} не найден для серии {}", pdfFile.getName(), seriesName);
            return;
        }

        // Если нашли PDF -> создаём новый BoilerSeriesPassport
        BoilerSeriesPassport passport = BoilerSeriesPassport.builder()
                .ruTitle("Паспорт " + seriesName)
                .seriesList(new ArrayList<>())
                .build();

        // Добавляем текущую серию в ManyToMany
        passport.getSeriesList().add(series);

        // Сохраняем Passport в БД, чтобы получить ID
        passport = boilerSeriesPassportRepository.save(passport);

        // Загружаем PDF через StorageService (нужен метод для локальных PDF)
        try {
            FileData fileData = storageService.uploadLocalPdfForBoilerSeriesPassport(pdfFile, passport);
            // Прописываем связь (двухстороннюю) fileData <-> passport
            passport.setFile(fileData);

            // Снова сохраняем Passport
            passport = boilerSeriesPassportRepository.save(passport);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке PDF-файла ({}) для BoilerSeriesPassport серии: {}", pdfFile.getName(), seriesName, e);
        }

        // Добавляем созданный паспорт в series.getBoilerSeriesPassports()
        series.getBoilerSeriesPassports().add(passport);

        // И сохраняем саму Series, чтобы в промежуточной таблице прописалась связь
        seriesRepository.save(series);
    }

    /**
     * Парсим преимущества (Advantage), лежащие в папках "УРОВНИ ЗАЩИТЫ", "ОСОБЕННОСТИ КОНСТРУКЦИИ" и "КОМФОРТ".
     */
    private void parseAdvantages(File seriesDir, Series series) {
        // 1. УРОВНИ ЗАЩИТЫ -> PROTECTION
        File protectionDir = new File(seriesDir, "УРОВНИ ЗАЩИТЫ");
        if (protectionDir.exists() && protectionDir.isDirectory()) {
            parseAdvantagesFromFolder(protectionDir, CategoryOfAdvantage.PROTECTION, series);
        }

        // 2. ОСОБЕННОСТИ КОНСТРУКЦИИ -> CONSTRUCTION
        File constructionDir = new File(seriesDir, "ОСОБЕННОСТИ КОНСТРУКЦИИ");
        if (constructionDir.exists() && constructionDir.isDirectory()) {
            parseAdvantagesFromFolder(constructionDir, CategoryOfAdvantage.CONSTRUCTION, series);
        }

        // 3. КОМФОРТ -> COMFORT
        File comfortDir = new File(seriesDir, "КОМФОРТ");
        if (comfortDir.exists() && comfortDir.isDirectory()) {
            parseAdvantagesFromFolder(comfortDir, CategoryOfAdvantage.COMFORT, series);
        }
    }

    /**
     * Проходимся по файлам (png) внутри указанной папки,
     * создаём Advantage с нужной категорией и привязываем его к Series.
     */
    private void parseAdvantagesFromFolder(File folder, CategoryOfAdvantage category, Series series) {
        File[] advantageFiles = folder.listFiles(
                file -> file.isFile() && file.getName().toLowerCase().endsWith(".png")
        );
        if (advantageFiles == null || advantageFiles.length == 0) return;

        for (File pngFile : advantageFiles) {
            String filename = pngFile.getName();
            String advantageTitle = filename.substring(0, filename.lastIndexOf('.')).trim();

            Advantage advantage = Advantage.builder()
                    .title(advantageTitle)
                    .category(category)
                    .series(new ArrayList<>())
                    .build();

            advantage.getSeries().add(series);
            advantage = advantageRepository.save(advantage);

            try {
                FileData savedFileData = storageService.uploadLocalImageToFileSystemForAdvantage(pngFile, advantage);
                advantage.setFileData(savedFileData);
                advantage = advantageRepository.save(advantage);
            } catch (IOException e) {
                logger.error("Ошибка при загрузке PNG файла ({}) для Advantage: {}", pngFile.getName(), advantageTitle, e);
            }
        }
    }

    private void parseAndSaveBoilersWithValues(File excelFile, Series series, List<Characteristic> characteristics) {
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                logger.warn("Пустая строка заголовков в файле: {}", excelFile.getName());
                return;
            }

            for (int colIndex = 2; colIndex < headerRow.getLastCellNum(); colIndex++) {
                Cell headerCell = headerRow.getCell(colIndex);
                if (headerCell != null) {
                    try {
                        Long boilerNumber = null;

                        if (headerCell.getCellType() == CellType.NUMERIC) {
                            boilerNumber = (long) headerCell.getNumericCellValue();
                        } else if (headerCell.getCellType() == CellType.STRING) {
                            String cellValue = headerCell.getStringCellValue().trim();
                            boilerNumber = Long.parseLong(cellValue.replaceAll("\\D+", ""));
                        }

                        if (boilerNumber == null) continue;

                        Boiler boiler = Boiler.builder()
                                .barcode(System.currentTimeMillis() + colIndex)
                                .number(boilerNumber)
                                .series(series)
                                .values(new ArrayList<>())
                                .build();
                        boiler = boilerRepository.save(boiler);

                        boolean hasValues = false;

                        for (Row row : sheet) {
                            if (row.getRowNum() == 0) continue;

                            int characteristicIndex = row.getRowNum() - 1;

                            if (characteristicIndex < 0 || characteristicIndex >= characteristics.size()) {
                                String characteristicTitle = (row.getCell(0) != null)
                                        ? row.getCell(0).getStringCellValue().trim()
                                        : "Неизвестная характеристика";

                                logger.error("Ошибка добавления: некорректный индекс характеристики {} для серии {} в файле {}",
                                        characteristicTitle, series.getDescription(), excelFile.getName());
                                continue;
                            }

                            Characteristic characteristic = characteristics.get(characteristicIndex);

                            Cell valueCell = row.getCell(colIndex);
                            if (valueCell == null || valueCell.getCellType() == CellType.BLANK) {
                                logger.warn("Нет значения для характеристики: {} в серии: {}",
                                        characteristic.getTitle(), series.getDescription());
                                continue;
                            }

                            String value = "";
                            if (valueCell.getCellType() == CellType.STRING) {
                                value = valueCell.getStringCellValue().trim();
                            } else if (valueCell.getCellType() == CellType.NUMERIC) {
                                value = Double.toString(valueCell.getNumericCellValue());
                            }

                            if (!value.isEmpty()) {
                                hasValues = true;

                                Value boilerValue = Value.builder()
                                        .characteristic(characteristic)
                                        .Value(value)
                                        .boilers(new ArrayList<>())
                                        .build();

                                boilerValue.getBoilers().add(boiler);
                                boiler.getValues().add(boilerValue);

                                valueRepository.save(boilerValue);
                            }
                        }

                        if (!hasValues) {
                            logger.warn("Нет значений для бойлера: {} в серии: {}", boiler.getNumber(), series.getDescription());
                        }

                        boilerRepository.save(boiler);
                    } catch (NumberFormatException e) {
                        logger.error("Пропущен некорректный номер бойлера в колонке {} в файле {}: {}",
                                colIndex, excelFile.getName(), e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            logger.error("Ошибка при чтении файла Excel: {}", excelFile.getPath(), e);
        }
    }

    private List<Characteristic> parseCharacteristicsFromExcel(File excelFile) {
        List<Characteristic> characteristics = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            System.out.println("Начало парсинга характеристик из файла: " + excelFile.getName());
            printExcelContent(workbook, excelFile.getName());
            Sheet sheet = workbook.getSheetAt(0); // Открываем первый лист

            for (Row row : sheet) {
                // Начинаем с первой строки данных (вторая строка в Excel, индекс 1)
                if (row.getRowNum() < 1) continue;

                Cell characteristicCell = row.getCell(0);
                if (characteristicCell == null ||
                        characteristicCell.getCellType() == CellType.BLANK ||
                        characteristicCell.getStringCellValue().trim().isEmpty() ||
                        "Пустая ячейка".equalsIgnoreCase(characteristicCell.getStringCellValue().trim())) {
                    System.out.println("Прерываем цикл на строке: " + (row.getRowNum() + 1) +
                            " из-за пустой, пробельной ячейки или значения 'Пустая ячейка'");
                    break;
                }

                Cell unitCell = row.getCell(1);

                String characteristicTitle = characteristicCell.getStringCellValue().trim();
                String unitName = (unitCell != null && unitCell.getCellType() == CellType.STRING)
                        ? unitCell.getStringCellValue().trim()
                        : null;

                if (!characteristicTitle.isEmpty()) {
                    logger.info("Название характеристики: {}", characteristicTitle);
                    Characteristic characteristic = Characteristic.builder()
                            .title(characteristicTitle)
                            .units(new ArrayList<>())
                            .build();

                    if (unitName != null && !unitName.isEmpty()) {
                        Unit unit = Unit.builder()
                                .name(unitName)
                                .characteristics(new ArrayList<>())
                                .build();

                        unit.getCharacteristics().add(characteristic);
                        characteristic.getUnits().add(unit);
                        unitRepository.save(unit);
                    }

                    characteristic = characteristicRepository.save(characteristic);
                    characteristics.add(characteristic);
                }
            }

        } catch (IOException e) {
            logger.error("Ошибка при чтении Excel файла: {}", excelFile.getPath(), e);
        }

        return characteristics;
    }

    private Series extractSeriesData(String seriesName) {
        // Пример шаблона:  ^(\w+)(\d+)-(\\d+)(\\w+)?$
        Pattern pattern = Pattern.compile("^(\\w+)(\\d+)-(\\d+)(\\w+)?$");
        Matcher matcher = pattern.matcher(seriesName);
        if (matcher.matches()) {
            String prefix = matcher.group(1);
            int startRange = Integer.parseInt(matcher.group(2));
            int endRange = Integer.parseInt(matcher.group(3));
            String suffix = matcher.group(4);

            return Series.builder()
                    .prefix(prefix)
                    .startRange(startRange)
                    .endRange(endRange)
                    .suffix(suffix)
                    .description("Серия " + seriesName)
                    .build();
        }
        System.out.println("Не удалось распознать серию: " + seriesName);
        return null;
    }

    private String extractTitle(String folderName) {
        int bracketIndex = folderName.indexOf(" (");
        if (bracketIndex != -1) {
            return folderName.substring(0, bracketIndex).trim();
        }
        return folderName.trim();
    }

    private String extractDescription(String folderName) {
        Pattern pattern = Pattern.compile("\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(folderName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private void printExcelContent(Workbook workbook, String fileName) {
        System.out.println("Содержимое файла: " + fileName);

        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            System.out.println("Лист: " + sheet.getSheetName());

            for (Row row : sheet) {
                StringBuilder rowContent = new StringBuilder("Строка " + row.getRowNum() + ": ");
                for (Cell cell : row) {
                    String cellValue = getCellValueAsString(cell);
                    rowContent.append(cellValue).append(" | ");
                }
                System.out.println(rowContent);
            }
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "null";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return Double.toString(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "Пустая ячейка";
            default:
                return "Неизвестный тип ячейки";
        }
    }
}
