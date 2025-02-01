package fr.mossaab.security.service.init;

import fr.mossaab.security.entities.*;
import fr.mossaab.security.entities.Error;
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

    private static final String FOLDER_PATH = "/app/uploads/system_initialization";
    private static final Logger logger = LoggerFactory.getLogger(ParserService.class);

    @Autowired
    private SparePartRepository sparePartRepository;
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

    // ==================== NEW: Репозиторий для сущности Error ====================
    @Autowired
    private ErrorRepository errorRepository;

    // Сервис для загрузки файлов (Series, Advantage, BoilerSeriesPassport и т.д.)
    @Autowired
    private StorageService storageService;

    @Autowired
    private ExplosionDiagramRepository explosionDiagramRepository;
    @Autowired
    private AttributeRepository attributeRepository;

    /**
     * Новый метод, парсящий папки "Запчасти-<номер>" и внутри — запчасти.
     */
    private void parseSpareParts(File seriesDir, Series series) {
        // Проверим, есть ли у серии взрыв-схема
        ExplosionDiagram explosionDiagram = series.getExplosionDiagram();
        if (explosionDiagram == null) {
            logger.info("У серии '{}' нет взрыв-схемы, пропускаем парсинг запчастей.", series.getDescription());
            return;
        }

        // Все подпапки внутри директории серии
        File[] subFolders = seriesDir.listFiles(File::isDirectory);
        if (subFolders == null) return;

        // Загружаем все бойлеры (чтобы потом фильтровать в коде)
        List<Boiler> allBoilers = boilerRepository.findAll();

        // Шаблон для имени папки "Запчасти-<число>"
        Pattern zapchastiPattern = Pattern.compile("^Запчасти-(\\d+)$");

        for (File zapchastiDir : subFolders) {
            Matcher matcher = zapchastiPattern.matcher(zapchastiDir.getName());
            if (!matcher.matches()) {
                // Это не папка вида "Запчасти-...", пропускаем
                continue;
            }

            // Извлекаем номер бойлера
            String boilerNumberStr = matcher.group(1);
            long boilerNumber;
            try {
                boilerNumber = Long.parseLong(boilerNumberStr);
            } catch (NumberFormatException e) {
                logger.warn("Некорректный формат номера бойлера: {}", boilerNumberStr);
                continue;
            }

            // Фильтруем из списка allBoilers нужный бойлер
            Boiler targetBoiler = null;
            for (Boiler b : allBoilers) {
                if (b.getSeries() != null
                        && b.getSeries().getId().equals(series.getId())
                        && b.getNumber() == boilerNumber) {
                    targetBoiler = b;
                    break;
                }
            }

            if (targetBoiler == null) {
                logger.warn("Бойлер с number={} не найден в серии '{}'", boilerNumber, series.getDescription());
                continue;
            }

            // Внутри "Запчасти-<номер>" ищем подпапки, каждая = артикул запчасти
            File[] sparePartArticleFolders = zapchastiDir.listFiles(File::isDirectory);
            if (sparePartArticleFolders == null) continue;

            for (File articleFolder : sparePartArticleFolders) {
                String articleNumber = articleFolder.getName().trim();  // например "QM-4-24-00-00000235"

                // Ищем PNG-файлы внутри папки
                File[] pngFiles = articleFolder.listFiles(file ->
                        file.isFile() && file.getName().toLowerCase().endsWith(".png"));
                if (pngFiles == null || pngFiles.length == 0) {
                    logger.warn("Папка {} не содержит PNG-файлов с названием запчасти.", articleFolder.getPath());
                    continue;
                }

                // Предположим, в папке ровно 1 png = 1 запчасть
                File pngFile = pngFiles[0];

                // Наименование запчасти = имя файла без .png
                String fileName = pngFile.getName();  // например "NTC Темп. датчик подачи.png"
                String sparePartName = fileName.substring(0, fileName.lastIndexOf('.')).trim();

                // Создаём сущность SparePart
                SparePart sparePart = SparePart.builder()
                        .articleNumber(articleNumber)
                        .name(sparePartName)
                        .explosionDiagram(explosionDiagram)
                        .build();

                // Связь ManyToMany с бойлерами
                sparePart.getBoilers().add(targetBoiler);

                // Сохраняем
                sparePart = sparePartRepository.save(sparePart);

                // Загружаем картинку и прописываем FileData
                try {
                    FileData fileData = storageService.uploadLocalImageToFileSystemForSparePart(pngFile, sparePart);
                    sparePart.setFileData(fileData);
                    sparePartRepository.save(sparePart);
                } catch (IOException e) {
                    logger.error("Ошибка загрузки PNG-файла '{}': {}", pngFile.getPath(), e.getMessage());
                }
            }
        }
    }


    /**
     * Парсит файл "Атрибуты.xlsx" для заданной серии.
     * Формат:
     *   - Первый столбец (колонка 0) содержит строки с названиями атрибутов.
     *   - Начинаем парсинг со второй строки (rowIndex = 1), чтобы пропустить заголовок.
     */
    private void parseAttributesForSeries(File attributesFile, Series series) {
        try (FileInputStream fis = new FileInputStream(attributesFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // берём первый лист
            if (sheet == null) {
                logger.warn("Лист 0 не найден в файле атрибутов: {}", attributesFile.getName());
                return;
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                // Берём ячейку из первого столбца (0), где хранится название атрибута
                Cell attributeCell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                if (attributeCell == null) {
                    // Пустая ячейка, пропускаем
                    continue;
                }

                // Преобразуем ячейку в строку
                String title = getStringValue(attributeCell).trim();

                // Если после trim() строка пустая, пропускаем
                if (title.isEmpty()) {
                    continue;
                }

                // Создаём и сохраняем Attribute
                Attribute attributeEntity = Attribute.builder()
                        .title(title)
                        .series(series)
                        .build();

                attributeRepository.save(attributeEntity);
            }

        } catch (IOException e) {
            logger.error("Ошибка при чтении файла Атрибуты.xlsx для серии '{}': {}",
                    series.getDescription(), e.getMessage());
        }
    }


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

                    // 1) Создаём Type
                    Type type = Type.builder()
                            .title(title)
                            .description(description != null ? description : "Описание отсутствует")
                            .build();

                    // 2) Сразу сохраняем Type в БД, чтобы у него появился ID,
                    //    и он не считался "transient"
                    type = typeRepository.save(type);

                    // 3) Теперь можем парсить Kind, внутри которого:
                    //    kind.setType(type) и kindRepository.save(kind) — уже безопасно
                    List<Kind> kinds = parseKinds(dir, type);
                    type.setKinds(kinds); // связываем их в обе стороны

                    // Можно ещё раз сохранить, чтобы зафиксировать kinds в самом type
                    type = typeRepository.save(type);

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

                    // Ищем Excel-файл с характеристиками (например "QM4-24.xlsx")
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

                    // ==================== NEW: Парсим "Ошибки.xlsx", если есть ====================
                    File errorsFile = new File(seriesDir, "Ошибки.xlsx");
                    if (errorsFile.exists()) {
                        parseErrorsForSeries(errorsFile, series);
                    } else {
                        // Если файла нет, серия без ошибок
                        logger.info("Ошибки.xlsx не найден для серии {}", seriesName);
                    }
                    File attributesFile = new File(seriesDir, "Атрибуты.xlsx");
                    if (attributesFile.exists()) {
                        parseAttributesForSeries(attributesFile, series);
                    } else {
                        logger.info("Атрибуты.xlsx не найден для серии {}", seriesName);
                    }
                    // ==================== /NEW ====================

                    // Парсим Advantage (три папки) для текущей серии
                    parseAdvantages(seriesDir, series);

                    // NEW: Парсим BoilerSeriesPassport (PDF) для текущей серии
                    parseBoilerSeriesPassport(seriesDir, series);
                    parseExplosionDiagram(seriesDir, series);
                    // =====================================
                    // ВАЖНО! Теперь вызываем парсинг "Запчасти"
                    // =====================================
                    parseSpareParts(seriesDir, series);
                    seriesList.add(series);
                }
            }
        }
        return seriesList;
    }

    /**
     * ==================== NEW: Метод парсинга "Ошибки.xlsx" ====================
     * Формат:
     * - Первый столбец (индекс 0) -> code
     * - Второй столбец (индекс 1) -> cause
     * - Третий столбец (индекс 2) -> description
     * Начинаем со второй строки (rowIndex = 1).
     */
    private void parseErrorsForSeries(File errorsFile, Series series) {
        try (FileInputStream fis = new FileInputStream(errorsFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // первый лист
            if (sheet == null) {
                logger.warn("Лист 0 не найден в файле ошибок: {}", errorsFile.getName());
                return;
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                // Ячейки могут отсутствовать, поэтому берём getCell(..., MissingCellPolicy.RETURN_BLANK_AS_NULL)
                Cell codeCell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                Cell causeCell = row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                Cell descriptionCell = row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

                // Преобразуем в строки, если не null
                String code = (codeCell != null) ? getStringValue(codeCell).trim() : "";
                String cause = (causeCell != null) ? getStringValue(causeCell).trim() : "";
                String description = (descriptionCell != null) ? getStringValue(descriptionCell).trim() : "";

                // Если все 3 поля пусты, значит строка пуста -> пропускаем
                if (code.isEmpty() && cause.isEmpty() && description.isEmpty()) {
                    continue;
                }

                Error errorEntity = Error.builder()
                        .code(code)
                        .cause(cause)
                        .description(description)
                        .series(series)  // Привязка к текущей серии
                        .build();

                errorRepository.save(errorEntity);
            }

        } catch (IOException e) {
            logger.error("Ошибка при чтении файла Ошибки.xlsx для серии '{}': {}",
                    series.getDescription(), e.getMessage());
        }
    }

    /**
     * Вспомогательный метод, превращающий любую ячейку в строку
     * (можно переиспользовать ваш метод getCellValueAsString).
     */
    private String getStringValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // При желании можно возвращать cell.getCellFormula()
                // но чаще нужно итоговое значение:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (IllegalStateException ex) {
                    // Если формула возвращает строку
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }
    // ==================== /NEW (парсинг Ошибки.xlsx) ====================

    /**
     * Парсим PDF-файл, соответствующий текущей серии (если он есть).
     */
    private void parseBoilerSeriesPassport(File seriesDir, Series series) {
        String seriesName = series.getPrefix() + series.getStartRange() + "-" + series.getEndRange();
        if (series.getSuffix() != null && !series.getSuffix().isEmpty()) {
            seriesName += series.getSuffix(); // формируем полное имя (например, "QM4-24")
        }

        File pdfFile = new File(seriesDir, seriesName + ".pdf");
        if (!pdfFile.exists()) {
            logger.info("PDF-файл {} не найден для серии {}", pdfFile.getName(), seriesName);
            return;
        }

        BoilerSeriesPassport passport = BoilerSeriesPassport.builder()
                .ruTitle("Паспорт " + seriesName)
                .seriesList(new ArrayList<>())
                .build();

        passport.getSeriesList().add(series);
        passport = boilerSeriesPassportRepository.save(passport);

        try {
            FileData fileData = storageService.uploadLocalPdfForBoilerSeriesPassport(pdfFile, passport);
            passport.setFile(fileData);
            passport = boilerSeriesPassportRepository.save(passport);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке PDF-файла ({}) для BoilerSeriesPassport серии: {}", pdfFile.getName(), seriesName, e);
        }

        series.getBoilerSeriesPassports().add(passport);
        seriesRepository.save(series);
    }

    /**
     * Парсим преимущества (Advantage) в подпапках "УРОВНИ ЗАЩИТЫ", "ОСОБЕННОСТИ КОНСТРУКЦИИ" и "КОМФОРТ".
     */
    private void parseAdvantages(File seriesDir, Series series) {
        File protectionDir = new File(seriesDir, "УРОВНИ ЗАЩИТЫ");
        if (protectionDir.exists() && protectionDir.isDirectory()) {
            parseAdvantagesFromFolder(protectionDir, CategoryOfAdvantage.PROTECTION, series);
        }

        File constructionDir = new File(seriesDir, "ОСОБЕННОСТИ КОНСТРУКЦИИ");
        if (constructionDir.exists() && constructionDir.isDirectory()) {
            parseAdvantagesFromFolder(constructionDir, CategoryOfAdvantage.CONSTRUCTION, series);
        }

        File comfortDir = new File(seriesDir, "КОМФОРТ");
        if (comfortDir.exists() && comfortDir.isDirectory()) {
            parseAdvantagesFromFolder(comfortDir, CategoryOfAdvantage.COMFORT, series);
        }
    }

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

    private void parseExplosionDiagram(File seriesDir, Series series) {
        // Ищем файл "взрыв-схема.pdf" в текущей директории серии
        File explosionFile = new File(seriesDir, "Взрыв-схема.pdf");
        if (!explosionFile.exists()) {
            // Если файла нет — просто выходим (не ломаем логику)
            logger.info("Файл взрыв-схема.pdf не найден для серии {}", series.getDescription());
            return;
        }

        // Формируем читабельное имя ("Взрыв схема QM4-24" и т.п.)
        String seriesFullName = series.getPrefix() + series.getStartRange() + "-" + series.getEndRange();
        if (series.getSuffix() != null && !series.getSuffix().isEmpty()) {
            seriesFullName += series.getSuffix();
        }

        // Создаём взрыв-схему
        ExplosionDiagram explosionDiagram = ExplosionDiagram.builder()
                .name("Взрыв схема " + seriesFullName) // nsmt: "Взрыв схема " + название серии
                .build();
        explosionDiagram = explosionDiagramRepository.save(explosionDiagram);

        try {
            // Загружаем PDF
            FileData fileData = storageService.uploadLocalPdfForExplosionDiagram(explosionFile, explosionDiagram);
            explosionDiagram.setFileData(fileData);

            // Устанавливаем связь "1:1" (Series <-> ExplosionDiagram)
            explosionDiagram.setSeries(series);
            series.setExplosionDiagram(explosionDiagram);

            // Сохраняем обновлённые сущности
            explosionDiagram = explosionDiagramRepository.save(explosionDiagram);
            seriesRepository.save(series);

            logger.info("Взрыв-схема успешно добавлена для серии {}", seriesFullName);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке взрыв-схемы (PDF) для серии {}: {}", seriesFullName, e.getMessage());
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

            // Начинаем со столбца 2 (colIndex = 2), предполагая,
            // что первые два столбца — служебные/другие данные
            for (int colIndex = 2; colIndex < headerRow.getLastCellNum(); colIndex++) {
                Cell headerCell = headerRow.getCell(colIndex);
                if (headerCell != null) {
                    try {
                        Long boilerNumber = null;
                        String barcode = null;

                        // 1. Если ячейка — числовая
                        if (headerCell.getCellType() == CellType.NUMERIC) {
                            // Берём её значение как номер бойлера
                            boilerNumber = (long) headerCell.getNumericCellValue();
                            // Штрих-кода в скобках быть не может, значит - дефолт
                            barcode = String.valueOf(System.currentTimeMillis() + colIndex);
                        }
                        // 2. Если ячейка — текстовая
                        else if (headerCell.getCellType() == CellType.STRING) {
                            String cellValue = headerCell.getStringCellValue().trim();

                            // Регулярное выражение:
                            // ^(\d+)           — в начале строки одна или более цифр (будущий номер)
                            // (?:\(([^)]+)\))? — необязательный фрагмент (скобки и их содержимое), внутри которых всё, кроме закрывающей скобки
                            // $                — конец строки
                            Pattern pattern = Pattern.compile("^(\\d+)(?:\\(([^)]+)\\))?$");
                            Matcher matcher = pattern.matcher(cellValue);

                            if (matcher.matches()) {
                                // Группа (1) — это номер бойлера
                                boilerNumber = Long.parseLong(matcher.group(1));

                                // Группа (2) — это содержимое в скобках (штрих-код), если оно есть
                                String barCodeCandidate = matcher.group(2);
                                if (barCodeCandidate != null && !barCodeCandidate.isEmpty()) {
                                    barcode = barCodeCandidate.trim();
                                } else {
                                    // Если ничего в скобках не было, используем дефолт
                                    barcode = String.valueOf(System.currentTimeMillis() + colIndex);
                                }
                            } else {
                                // Если не подошло под шаблон (например, строка "20A")
                                logger.warn("Строка не соответствует ожидаемому формату 'число(опция)': {}", cellValue);
                                continue;
                            }
                        }
                        // 3. Другие типы ячеек (BLANK, FORMULA, BOOLEAN и т.п.) — пропускаем
                        else {
                            continue;
                        }

                        // Если номер не извлекли, пропускаем
                        if (boilerNumber == null) {
                            continue;
                        }

                        // Создаём объект Boiler
                        Boiler boiler = Boiler.builder()
                                .barcode(Long.valueOf(barcode))
                                .number(boilerNumber)
                                .series(series)
                                .values(new ArrayList<>())
                                .build();
                        // Сохраняем начальную запись о бойлере
                        boiler = boilerRepository.save(boiler);

                        boolean hasValues = false;

                        // Перебираем строки, начиная со второй (строка 0 — заголовки)
                        for (Row row : sheet) {
                            if (row.getRowNum() == 0) continue;

                            // Индекс характеристики соответствует номеру строки - 1
                            int characteristicIndex = row.getRowNum() - 1;

                            if (characteristicIndex < 0 || characteristicIndex >= characteristics.size()) {
                                String characteristicTitle = (row.getCell(0) != null)
                                        ? row.getCell(0).getStringCellValue().trim()
                                        : "Неизвестная характеристика";

                                logger.error("Ошибка добавления: некорректный индекс характеристики {} " +
                                        "для серии {} в файле {}", characteristicTitle, series.getDescription(), excelFile.getName());
                                continue;
                            }

                            Characteristic characteristic = characteristics.get(characteristicIndex);

                            Cell valueCell = row.getCell(colIndex);
                            if (valueCell == null || valueCell.getCellType() == CellType.BLANK) {
                                logger.warn("Нет значения для характеристики: {} в серии: {}",
                                        characteristic.getTitle(), series.getDescription());
                                continue;
                            }

                            // Приводим значение к строке
                            String value = "";
                            if (valueCell.getCellType() == CellType.STRING) {
                                value = valueCell.getStringCellValue().trim();
                            } else if (valueCell.getCellType() == CellType.NUMERIC) {
                                value = Double.toString(valueCell.getNumericCellValue());
                            }

                            // Записываем значение, если оно не пустое
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

                        // Если для этого бойлера не было заполнено ни одного значения, логируем
                        if (!hasValues) {
                            logger.warn("Нет значений для бойлера: {} в серии: {}", boiler.getNumber(), series.getDescription());
                        }

                        // Снова сохраняем бойлер, чтобы зафиксировать связанные данные
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
                String unitName = unitCell != null && unitCell.getCellType() == CellType.STRING
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
        // Пример шаблона:  ^(\w+)(\d+)-(\d+)(\w+)?$
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
