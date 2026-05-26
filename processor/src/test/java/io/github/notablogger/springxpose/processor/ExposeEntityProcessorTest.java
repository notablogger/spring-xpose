package io.github.notablogger.springxpose.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.*;

class ExposeEntityProcessorTest {

    private static final javax.tools.JavaFileObject BOOK_SOURCE = JavaFileObjects.forSourceString(
        "com.example.entity.Book",
        """
        package com.example.entity;
        import io.github.notablogger.springxpose.annotation.ExposeEntity;
        import jakarta.persistence.*;
        @Entity
        @ExposeEntity(path = "books")
        public class Book {
            @Id @GeneratedValue private Long id;
            private String title;
            private String author;
            public Long getId() { return id; }
            public void setId(Long id) { this.id = id; }
            public String getTitle() { return title; }
            public void setTitle(String title) { this.title = title; }
            public String getAuthor() { return author; }
            public void setAuthor(String author) { this.author = author; }
        }
        """
    );

    @Test
    void generatesAllFiveArtifacts() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(BOOK_SOURCE);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookRepository");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookDto");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookMapper");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookController");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookSecurityConfigurer");
    }

    @Test
    void dtoContainsScalarFields() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(BOOK_SOURCE);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookDto")
            .contentsAsUtf8String().contains("title");
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookDto")
            .contentsAsUtf8String().contains("author");
    }

    @Test
    void dtoAlwaysContainsId() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.Book",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.*;
                import jakarta.persistence.*;
                @Entity
                @ExposeEntity(path="books", ignoredFields={"title"})
                public class Book {
                    @Id @GeneratedValue private Long id;
                    private String title;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getTitle() { return title; }
                    public void setTitle(String t) { this.title = t; }
                }
                """
            )
        );

        assertThat(c).succeeded();
        String dto = c.generatedSourceFile("com.example.entity.generated.BookDto")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(dto.contains("id"), "DTO must always contain id even when not in ignoredFields");
        assertFalse(dto.contains("String title"), "ignoredFields should exclude 'title' from DTO");
    }

    @Test
    void ignoredFieldsExcludedFromDto() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.Book",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.*;
                import jakarta.persistence.*;
                @Entity
                @ExposeEntity(path="books", ignoredFields={"internalNotes", "auditLog"})
                public class Book {
                    @Id @GeneratedValue private Long id;
                    private String title;
                    private String internalNotes;
                    private String auditLog;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getTitle() { return title; }
                    public void setTitle(String t) { this.title = t; }
                    public String getInternalNotes() { return internalNotes; }
                    public void setInternalNotes(String s) { this.internalNotes = s; }
                    public String getAuditLog() { return auditLog; }
                    public void setAuditLog(String s) { this.auditLog = s; }
                }
                """
            )
        );

        assertThat(c).succeeded();
        String dto = c.generatedSourceFile("com.example.entity.generated.BookDto")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(dto.contains("title"), "non-ignored field 'title' must be in DTO");
        assertFalse(dto.contains("internalNotes"), "'internalNotes' in ignoredFields must be absent from DTO");
        assertFalse(dto.contains("auditLog"), "'auditLog' in ignoredFields must be absent from DTO");
    }

    @Test
    void mapperGeneratedWithSpringComponentModel() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(BOOK_SOURCE);

        assertThat(c).succeeded();
        String mapper = c.generatedSourceFile("com.example.entity.generated.BookMapper")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(mapper.contains("componentModel"), "Mapper must have componentModel");
        assertTrue(mapper.contains("spring"), "Mapper componentModel must be spring");
        assertTrue(mapper.contains("toDto"), "Mapper must have toDto method");
        assertTrue(mapper.contains("toDtoList"), "Mapper must have toDtoList method");
    }

    @Test
    void mapperGeneratesRelationIdMappingForNonObjectMode() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.Category",
                """
                package com.example.entity;
                import jakarta.persistence.*;
                @Entity public class Category {
                    @Id @GeneratedValue private Long id;
                    private String name;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getName() { return name; }
                    public void setName(String n) { this.name = n; }
                }
                """
            ),
            JavaFileObjects.forSourceString("com.example.entity.Book",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.*;
                import jakarta.persistence.*;
                @Entity
                @ExposeEntity(path="books", relationMode=RelationMode.ALWAYS_IDS)
                public class Book {
                    @Id @GeneratedValue private Long id;
                    private String title;
                    @ManyToOne Category category;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getTitle() { return title; }
                    public void setTitle(String t) { this.title = t; }
                    public Category getCategory() { return category; }
                    public void setCategory(Category c) { this.category = c; }
                }
                """
            )
        );

        assertThat(c).succeeded();
        String mapper = c.generatedSourceFile("com.example.entity.generated.BookMapper")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(mapper.contains("category.id"), "@Mapping source must be category.id for ID mode");
        assertTrue(mapper.contains("categoryId"), "@Mapping target must be categoryId");

        String dto = c.generatedSourceFile("com.example.entity.generated.BookDto")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(dto.contains("categoryId"), "DTO must have categoryId field in ID mode");
        assertFalse(dto.contains("Category category"), "DTO must NOT have full Category object in ID mode");
    }

    @Test
    void mapperGeneratesNoMappingAnnotationForAlwaysObject() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.Category",
                """
                package com.example.entity;
                import jakarta.persistence.*;
                @Entity public class Category {
                    @Id @GeneratedValue private Long id;
                    private String name;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getName() { return name; }
                    public void setName(String n) { this.name = n; }
                }
                """
            ),
            JavaFileObjects.forSourceString("com.example.entity.Book",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.*;
                import jakarta.persistence.*;
                @Entity
                @ExposeEntity(path="books", relationMode=RelationMode.ALWAYS_OBJECT)
                public class Book {
                    @Id @GeneratedValue private Long id;
                    private String title;
                    @ManyToOne Category category;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getTitle() { return title; }
                    public void setTitle(String t) { this.title = t; }
                    public Category getCategory() { return category; }
                    public void setCategory(Category c) { this.category = c; }
                }
                """
            )
        );

        assertThat(c).succeeded();
        String mapper = c.generatedSourceFile("com.example.entity.generated.BookMapper")
            .orElseThrow().getCharContent(false).toString();
        assertFalse(mapper.contains("categoryId"),
            "ALWAYS_OBJECT mapper must not have id-extraction @Mapping");
    }

    @Test
    void controllerReturnsDto() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(BOOK_SOURCE);

        assertThat(c).succeeded();
        String ctrl = c.generatedSourceFile("com.example.entity.generated.BookController")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(ctrl.contains("BookDto"), "Controller must return BookDto not the entity");
        assertTrue(ctrl.contains("mapper"), "Controller must inject mapper");
        assertTrue(ctrl.contains("mapper.toDto"), "Controller must call mapper.toDto");
        assertTrue(ctrl.contains("mapper.toDtoList"), "Controller must call mapper.toDtoList");
    }

    @Test
    void failsWhenNoIdField() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.NoIdEntity",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.ExposeEntity;
                import jakarta.persistence.*;
                @Entity
                @ExposeEntity
                public class NoIdEntity {
                    private String name;
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
                """
            )
        );

        assertFalse(c.status() == Compilation.Status.SUCCESS,
            "Compilation should have failed but succeeded");
        assertTrue(
            c.diagnostics().stream().anyMatch(d ->
                d.getKind() == Diagnostic.Kind.ERROR &&
                d.getMessage(null).contains("@ExposeEntity: no @Id field found")),
            "Expected error '@ExposeEntity: no @Id field found' not found");
    }
}
