package fr.mossaab.security.controller;

import fr.mossaab.security.entities.SparePart;
import fr.mossaab.security.entities.FileData;
import fr.mossaab.security.entities.ExplosionDiagram;
import fr.mossaab.security.repository.SparePartRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Tag(name = "Запчасти", description = "API для работы с запчастями")
@RestController
@RequiredArgsConstructor
@RequestMapping("/spare-part")
public class SparePartController {

    private final SparePartRepository sparePartRepository;

    // DTO для SparePart с идентификаторами связных сущностей
    public static class SparePartDto {
        public Long id;
        public String articleNumber;
        public String name;
        public BigDecimal ascPriceYuan;
        public BigDecimal wholesalePriceYuan;
        public BigDecimal retailPriceYuan;
        public BigDecimal ascPriceRub;
        public BigDecimal wholesalePriceRub;
        public BigDecimal retailPriceRub;
        public Long fileDataId;
        public Long explosionDiagramId;

        // Конструктор для создания DTO из сущности SparePart
        public SparePartDto(SparePart sparePart) {
            this.id = sparePart.getId();
            this.articleNumber = sparePart.getArticleNumber();
            this.name = sparePart.getName();
            this.ascPriceYuan = sparePart.getAscPriceYuan();
            this.wholesalePriceYuan = sparePart.getWholesalePriceYuan();
            this.retailPriceYuan = sparePart.getRetailPriceYuan();
            this.ascPriceRub = sparePart.getAscPriceRub();
            this.wholesalePriceRub = sparePart.getWholesalePriceRub();
            this.retailPriceRub = sparePart.getRetailPriceRub();
            this.fileDataId = sparePart.getFileData() != null ? sparePart.getFileData().getId() : null;
            this.explosionDiagramId = sparePart.getExplosionDiagram() != null ? sparePart.getExplosionDiagram().getId() : null;
        }
    }

    @Operation(summary = "Получить все запчасти с идентификаторами связных сущностей")
    @GetMapping("get-all")
    public List<SparePartDto> getAllSpareParts() {
        List<SparePartDto> sparePartDtos = new ArrayList<>();
        for (SparePart sparePart : sparePartRepository.findAll()) {
            sparePartDtos.add(new SparePartDto(sparePart));
        }
        return sparePartDtos;
    }

    @Operation(summary = "Получить запчасть по идентификатору")
    @GetMapping("/find-by-id/{id}")
    public ResponseEntity<SparePartDto> getSparePartById(@PathVariable Long id) {
        Optional<SparePart> sparePart = sparePartRepository.findById(id);
        return sparePart.map(part -> ResponseEntity.ok(new SparePartDto(part)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Получить запчасть по артикулу")
    @GetMapping("/find-by-article/{articleNumber}")
    public ResponseEntity<SparePartDto> getSparePartByArticle(@PathVariable String articleNumber) {
        Optional<SparePart> sparePart = sparePartRepository.findByArticleNumber(articleNumber);
        return sparePart.map(part -> ResponseEntity.ok(new SparePartDto(part)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Получить запчасть по наименованию")
    @GetMapping("/find-by-name/{name}")
    public List<SparePartDto> getSparePartsByName(@PathVariable String name) {
        List<SparePartDto> sparePartDtos = new ArrayList<>();
        for (SparePart sparePart : sparePartRepository.findByName(name)) {
            sparePartDtos.add(new SparePartDto(sparePart));
        }
        return sparePartDtos;
    }

    @Operation(summary = "Удалить запчасть по идентификатору")
    @DeleteMapping("/delete-by-id/{id}")
    public ResponseEntity<Void> deleteSparePartById(@PathVariable Long id) {
        if (sparePartRepository.existsById(id)) {
            sparePartRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Удалить запчасть по артикулу")
    @DeleteMapping("/delete-by-article/{articleNumber}")
    public ResponseEntity<Void> deleteSparePartByArticle(@PathVariable String articleNumber) {
        Optional<SparePart> sparePart = sparePartRepository.findByArticleNumber(articleNumber);
        if (sparePart.isPresent()) {
            sparePartRepository.delete(sparePart.get());
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Удалить запчасть по наименованию")
    @DeleteMapping("/delete-by-name/{name}")
    public ResponseEntity<Void> deleteSparePartByName(@PathVariable String name) {
        List<SparePart> spareParts = sparePartRepository.findByName(name);
        if (!spareParts.isEmpty()) {
            sparePartRepository.deleteAll(spareParts);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Обновить запчасть по идентификатору")
    @PutMapping("/update-by-id/{id}")
    public ResponseEntity<SparePartDto> updateSparePart(@PathVariable Long id, @RequestBody SparePartDto dto) {
        Optional<SparePart> sparePartOpt = sparePartRepository.findById(id);
        if (sparePartOpt.isPresent()) {
            SparePart sparePart = sparePartOpt.get();
            sparePart.setArticleNumber(dto.articleNumber);
            sparePart.setName(dto.name);
            sparePart.setAscPriceYuan(dto.ascPriceYuan);
            sparePart.setWholesalePriceYuan(dto.wholesalePriceYuan);
            sparePart.setRetailPriceYuan(dto.retailPriceYuan);
            sparePart.setAscPriceRub(dto.ascPriceRub);
            sparePart.setWholesalePriceRub(dto.wholesalePriceRub);
            sparePart.setRetailPriceRub(dto.retailPriceRub);
            // Обновление связных сущностей, если они уже существуют
            if (dto.fileDataId != null) {
                FileData fileData = new FileData();
                fileData.setId(dto.fileDataId);
                sparePart.setFileData(fileData);
            }
            if (dto.explosionDiagramId != null) {
                ExplosionDiagram explosionDiagram = new ExplosionDiagram();
                explosionDiagram.setId(dto.explosionDiagramId);
                sparePart.setExplosionDiagram(explosionDiagram);
            }
            sparePartRepository.save(sparePart);
            return ResponseEntity.ok(new SparePartDto(sparePart));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Создать новую запчасть")
    @PostMapping("add")
    public ResponseEntity<SparePartDto> createSparePart(@RequestBody SparePartDto dto) {
        SparePart sparePart = new SparePart();
        sparePart.setArticleNumber(dto.articleNumber);
        sparePart.setName(dto.name);
        sparePart.setAscPriceYuan(dto.ascPriceYuan);
        sparePart.setWholesalePriceYuan(dto.wholesalePriceYuan);
        sparePart.setRetailPriceYuan(dto.retailPriceYuan);
        sparePart.setAscPriceRub(dto.ascPriceRub);
        sparePart.setWholesalePriceRub(dto.wholesalePriceRub);
        sparePart.setRetailPriceRub(dto.retailPriceRub);
        if (dto.fileDataId != null) {
            FileData fileData = new FileData();
            fileData.setId(dto.fileDataId);
            sparePart.setFileData(fileData);
        }
        if (dto.explosionDiagramId != null) {
            ExplosionDiagram explosionDiagram = new ExplosionDiagram();
            explosionDiagram.setId(dto.explosionDiagramId);
            sparePart.setExplosionDiagram(explosionDiagram);
        }
        SparePart savedSparePart = sparePartRepository.save(sparePart);
        return ResponseEntity.ok(new SparePartDto(savedSparePart));
    }
}
