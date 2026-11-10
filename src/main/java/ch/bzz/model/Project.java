package ch.bzz.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @Column(name = "project_name", nullable = false, unique = true, length = 100)
    private String projectName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
}
