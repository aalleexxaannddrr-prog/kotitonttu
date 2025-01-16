package fr.mossaab.security.controller;

import fr.mossaab.security.entities.Characteristic;
import fr.mossaab.security.entities.Unit;
import fr.mossaab.security.repository.CharacteristicRepository;
import fr.mossaab.security.repository.UnitRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/unit")
@Tag(name = "Единицы измерения", description = "API для работы с единицами измерения характеристик")
@RequiredArgsConstructor
public class UnitController {

    private final UnitRepository unitRepository;
    private final CharacteristicRepository characteristicRepository;

    // 1) Метод, который выводит все unit и к каждому юниту идентификаторы характеристик
    @GetMapping("/get-all")
    @Operation(summary = "Получить все единицы измерения и идентификаторы характеристик принадлежащих к каждой серии")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список единиц измерения")
    })
    public List<UnitDto> getAllUnits() {
        List<Unit> units = unitRepository.findAll();
        List<UnitDto> result = new ArrayList<>();

        for (Unit unit : units) {
            UnitDto dto = new UnitDto();
            dto.setId(unit.getId());
            dto.setName(unit.getName());
            List<Long> characteristicIds = new ArrayList<>();

            for (Characteristic characteristic : unit.getCharacteristics()) {
                characteristicIds.add(characteristic.getId());
            }

            dto.setCharacteristicIds(characteristicIds);
            result.add(dto);
        }

        return result;
    }

    // 2) Создание нового unit без связной сущности
    @PostMapping("/add-unit")
    @Operation(summary = "Создать новую единицу измерения без прикрепления характеристики")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Единица измерения создана")
    })
    public Unit createUnit(@RequestBody CreateUnitDto unitDto) {
        Unit unit = Unit.builder()
                .name(unitDto.getName())
                //.longName(unitDto.getLongName())
                .characteristics(new ArrayList<>())
                .build();
        return unitRepository.save(unit);
    }

    // 3) Обновление полей unit и добавление характеристики
    @PutMapping("/update/{id}")
    @Operation(summary = "Обновить данные единицы измерения прикрепляя характеристику")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Единица измерения обновлена"),
            @ApiResponse(responseCode = "404", description = "Единица измерения не найдена")
    })
    public Unit updateUnit(@PathVariable Long id, @RequestBody UpdateUnitDto unitDto) {
        Optional<Unit> optionalUnit = unitRepository.findById(id);
        if (optionalUnit.isEmpty()) {
            throw new RuntimeException("Unit not found");
        }

        Unit unit = optionalUnit.get();
        if (unitDto.getName() != null) {
            unit.setName(unitDto.getName());
        }
//        if (unitDto.getLongName() != null) {
//            unit.setLongName(unitDto.getLongName());
//        }

        // Добавление характеристики
        if (unitDto.getCharacteristicIds() != null && !unitDto.getCharacteristicIds().isEmpty()) {
            for (Long characteristicId : unitDto.getCharacteristicIds()) {
                Optional<Characteristic> characteristicOptional = characteristicRepository.findById(characteristicId);
                characteristicOptional.ifPresent(unit.getCharacteristics()::add);
            }
        }

        return unitRepository.save(unit);
    }

    // 4) Удаление unit по идентификатору
    @DeleteMapping("/delete-by-id/{id}")
    @Operation(summary = "Удалить единицу измерения по идентификатору")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Единица измерения удалена"),
            @ApiResponse(responseCode = "404", description = "Единица измерения не найдена")
    })
    public ResponseEntity<Void> deleteUnit(@PathVariable Long id) {
        // Поиск Unit по идентификатору
        Unit unit = unitRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Unit not found"));

        // Разрываем связи с характеристиками
        unit.getCharacteristics().forEach(characteristic -> characteristic.getUnits().remove(unit));
        unit.getCharacteristics().clear();

        // Удаление Unit
        unitRepository.delete(unit);

        return ResponseEntity.noContent().build();
    }


    // 5) Поиск unit по идентификатору и вывод всех полей и идентификаторов характеристик
    @GetMapping("/find-by-id/{id}")
    @Operation(summary = "Получить единицу измерения по идентификатору")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Единица измерения найдена"),
            @ApiResponse(responseCode = "404", description = "Единица измерения не найдена")
    })
    public UnitDto getUnitById(@PathVariable Long id) {
        Optional<Unit> optionalUnit = unitRepository.findById(id);
        if (optionalUnit.isEmpty()) {
            throw new RuntimeException("Unit not found");
        }

        Unit unit = optionalUnit.get();
        UnitDto dto = new UnitDto();
        dto.setId(unit.getId());
        dto.setName(unit.getName());

        List<Long> characteristicIds = new ArrayList<>();
        for (Characteristic characteristic : unit.getCharacteristics()) {
            characteristicIds.add(characteristic.getId());
        }
        dto.setCharacteristicIds(characteristicIds);

        return dto;
    }

    // DTO для вывода данных
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UnitDto {
        private Long id;
        @Schema(example = "м³/ч")
        private String name;
//        @Schema(example = "Кубических метров в час")
//        private String longName;
        private List<Long> characteristicIds;

        // Конструкторы, геттеры и сеттеры
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UpdateUnitDto {
        @Schema(example = "м³/ч", nullable = true)
        private String name;
//        @Schema(example = "Кубических метров в час", nullable = true)
//        private String longName;
        @Schema(nullable = true)
        private List<Long> characteristicIds;
        // Конструкторы, геттеры и сеттеры
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreateUnitDto {
        @Schema(example = "м³/ч")
        private String name;
    }
}