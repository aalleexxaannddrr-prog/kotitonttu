package fr.mossaab.security;

//import fr.mossaab.security.service.init.CreateServiceCentreService;
import fr.mossaab.security.service.init.UserCreateService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@SpringBootApplication
public class SecurityApplication {
    private boolean SchemaIsEmpty = false;
//    @Autowired
//    private BoilerCreateService boilerCreateService;
//    @Autowired
//    private PassportCreateService passportService;
//    @Autowired
//    private DocumentCreateService documentService;
//    @Autowired
//    private CreateServiceCentreService createServiceCentreService;
    @Autowired
    private UserCreateService userCreationService;

    public static void main(String[] args) {

        SpringApplication.run(SecurityApplication.class, args);
    }
    @Transactional
    @PostConstruct
    public void createSamplePresentation() throws IOException {
//        boilerCreateService.CreateBoilers();
//        passportService.createAndSavePassportData();
//        documentService.createAndSaveDocumentData();
        //createServiceCentreService.CreateServiceCentre();
        userCreationService.createUsers();
        System.out.println("Пример презентации с файлами успешно создан.");
    }


}
