package fr.mossaab.security;


import fr.mossaab.security.repository.TypeRepository;
import fr.mossaab.security.service.init.ParserService;
import fr.mossaab.security.service.init.ServiceCenterExcelParserService;
import fr.mossaab.security.service.init.UserCreateService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@SpringBootApplication
public class SecurityApplication {


    @Autowired
    private UserCreateService userCreationService;
    @Autowired
    private ParserService typeParserService;
    @Autowired
    private TypeRepository typeRepository;
    @Autowired
    private ServiceCenterExcelParserService serviceCenterExcelParserService;
    public static void main(String[] args) {
        SpringApplication.run(SecurityApplication.class, args);
    }
    @Transactional
    @PostConstruct
    public void createSamplePresentation() throws IOException {
        // Проверяем, есть ли записи в таблице Type
        if (typeRepository.count() == 0) {
            // Если записей нет, запускаем парсер
            typeParserService.parseAndSaveTypes();
            serviceCenterExcelParserService.parseAndSaveServiceCenters();
        }
        userCreationService.createUsers();
    }


}
