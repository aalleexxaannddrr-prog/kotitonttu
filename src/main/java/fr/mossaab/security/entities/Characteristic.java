package fr.mossaab.security.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="characteristics")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Characteristic {

    @Id
    @GeneratedValue
    private Long id;

    private String title;

    @ManyToMany(mappedBy = "characteristics")
    @JsonIgnore
    private List<Unit> units = new ArrayList<>();

    @ManyToMany(mappedBy = "characteristics")
    @JsonIgnore
    private List<Series> series =  new ArrayList<>();

}
