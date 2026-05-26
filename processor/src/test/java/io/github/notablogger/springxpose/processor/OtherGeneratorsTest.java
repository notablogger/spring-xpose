package io.github.notablogger.springxpose.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtherGeneratorsTest {

    private static final javax.tools.JavaFileObject CATEGORY = JavaFileObjects.forSourceString(
        "com.example.entity.Category",
        """
        package com.example.entity;
        import jakarta.persistence.*;
        @Entity
        public class Category {
            @Id @GeneratedValue private Long id;
            private String name;
            public Long getId() { return id; }
            public void setId(Long id) { this.id = id; }
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }
        """
    );

    private static final javax.tools.JavaFileObject BOOK = JavaFileObjects.forSourceString(
        "com.example.entity.Book",
        """
        package com.example.entity;
        import io.github.notablogger.springxpose.annotation.*;
        import jakarta.persistence.*;

        @Entity
        @ExposeEntity(path = "books", relationMode = RelationMode.ALWAYS_IDS, ignoredFields = {"internalNotes"})
        public class Book {
            @Id @GeneratedValue private Long id;
            private String title;
            private String internalNotes;
            @ManyToOne private Category category;

            public Long getId() { return id; }
            public void setId(Long id) { this.id = id; }
            public String getTitle() { return title; }
            public void setTitle(String title) { this.title = title; }
            public String getInternalNotes() { return internalNotes; }
            public void setInternalNotes(String internalNotes) { this.internalNotes = internalNotes; }
            public Category getCategory() { return category; }
            public void setCategory(Category category) { this.category = category; }
        }
        """
    );

    private Compilation compileBook() {
        return javac().withProcessors(new ExposeEntityProcessor()).compile(CATEGORY, BOOK);
    }

    @Test
    void repositoryGenerator_extendsJpaRepositoryWithEntityAndId() throws Exception {
        Compilation c = compileBook();

        assertThat(c).succeeded();
        String repo = c.generatedSourceFile("com.example.entity.generated.BookRepository")
            .orElseThrow().getCharContent(false).toString();

        assertTrue(repo.contains("extends JpaRepository<Book, Long>"),
            "Repository should extend JpaRepository<Book, Long>");
    }

    @Test
    void dtoGenerator_excludesIgnoredFieldsAndUsesRelationIdForAlwaysIds() throws Exception {
        Compilation c = compileBook();

        assertThat(c).succeeded();
        String dto = c.generatedSourceFile("com.example.entity.generated.BookDto")
            .orElseThrow().getCharContent(false).toString();

        assertTrue(dto.contains("Long categoryId"), "DTO should expose relation id in ALWAYS_IDS mode");
        assertFalse(dto.contains("internalNotes"), "DTO should exclude ignored fields");
    }

    @Test
    void requestDtoGenerator_excludesIdAndMapsRelationsAsIds() throws Exception {
        Compilation c = compileBook();

        assertThat(c).succeeded();
        String requestDto = c.generatedSourceFile("com.example.entity.generated.BookRequestDto")
            .orElseThrow().getCharContent(false).toString();

        assertFalse(requestDto.contains("Long id"), "Request DTO should not contain id");
        assertTrue(requestDto.contains("Long categoryId"), "Request DTO should use relation id fields");
    }

    @Test
    void mapperGenerator_containsRelationMappingAndUpdateMethod() throws Exception {
        Compilation c = compileBook();

        assertThat(c).succeeded();
        String mapper = c.generatedSourceFile("com.example.entity.generated.BookMapper")
            .orElseThrow().getCharContent(false).toString();

        assertTrue(mapper.contains("source = \"category.id\""),
            "Mapper should map relation object to categoryId");
        assertTrue(mapper.contains("void updateEntity"), "Mapper should generate updateEntity method");
    }
}

