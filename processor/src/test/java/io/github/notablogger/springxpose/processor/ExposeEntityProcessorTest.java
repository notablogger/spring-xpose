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
        assertThat(c).generatedSourceFile("com.example.entity.generated.BookRequestDto");
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
        assertTrue(mapper.contains("toEntity"), "Mapper must have toEntity method");
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

    @Test
    void requestDtoIsGenerated() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(BOOK_SOURCE);

        assertThat(c).succeeded();
        String reqDto = c.generatedSourceFile("com.example.entity.generated.BookRequestDto")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(reqDto.contains("title"), "RequestDto must contain non-ignored scalar fields");
        assertFalse(reqDto.contains("Long id"), "RequestDto must NOT contain id");
    }

    @Test
    void requestDtoExcludesIdAndIgnoredFields() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.Book",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.*;
                import jakarta.persistence.*;
                @Entity
                @ExposeEntity(path="books", ignoredFields={"internalNotes"})
                public class Book {
                    @Id @GeneratedValue private Long id;
                    private String title;
                    private String internalNotes;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getTitle() { return title; }
                    public void setTitle(String t) { this.title = t; }
                    public String getInternalNotes() { return internalNotes; }
                    public void setInternalNotes(String s) { this.internalNotes = s; }
                }
                """
            )
        );
        assertThat(c).succeeded();
        String reqDto = c.generatedSourceFile("com.example.entity.generated.BookRequestDto")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(reqDto.contains("title"), "RequestDto must include non-ignored 'title'");
        assertFalse(reqDto.contains("internalNotes"), "RequestDto must exclude ignoredFields");
        assertFalse(reqDto.contains("Long id"), "RequestDto must not include id field");
    }

    @Test
    void requestDtoRelationRepresentedAsId() throws Exception {
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
        String reqDto = c.generatedSourceFile("com.example.entity.generated.BookRequestDto")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(reqDto.contains("categoryId"),
            "RequestDto must represent ALWAYS_OBJECT relation as Long categoryId");
        assertFalse(reqDto.contains("Category category"),
            "RequestDto must NOT contain full Category object");
    }

    @Test
    void customMapper_mapperInterfaceHasNoMapperAnnotation() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.mapper.MyBookMapper",
                """
                package com.example.mapper;
                public class MyBookMapper {}
                """
            ),
            JavaFileObjects.forSourceString("com.example.entity.Book",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.*;
                import jakarta.persistence.*;
                @Entity
                @ExposeEntity(path="books", customMapper = com.example.mapper.MyBookMapper.class)
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
        String mapper = c.generatedSourceFile("com.example.entity.generated.BookMapper")
            .orElseThrow().getCharContent(false).toString();
        assertFalse(mapper.contains("@Mapper"),
            "When customMapper is set, the generated interface must NOT have @Mapper so MapStruct skips it");
        assertTrue(mapper.contains("toDto"), "Mapper interface contract must still have toDto");
        assertTrue(mapper.contains("toEntity"), "Mapper interface contract must still have toEntity");
    }

    @Test
    void validationAnnotationsCopiedToRequestDto() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.Book",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.*;
                import jakarta.persistence.*;
                import jakarta.validation.constraints.*;
                @Entity
                @ExposeEntity(path="books")
                public class Book {
                    @Id @GeneratedValue private Long id;
                    @NotBlank private String title;
                    @Positive private Double price;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public String getTitle() { return title; }
                    public void setTitle(String t) { this.title = t; }
                    public Double getPrice() { return price; }
                    public void setPrice(Double p) { this.price = p; }
                }
                """
            )
        );
        assertThat(c).succeeded();
        String reqDto = c.generatedSourceFile("com.example.entity.generated.BookRequestDto")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(reqDto.contains("NotBlank"), "RequestDto must carry @NotBlank from entity field");
        assertTrue(reqDto.contains("Positive"), "RequestDto must carry @Positive from entity field");
    }

    @Test
    void controllerUsesRequestDtoForWriteOps() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(BOOK_SOURCE);

        assertThat(c).succeeded();
        String ctrl = c.generatedSourceFile("com.example.entity.generated.BookController")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(ctrl.contains("BookRequestDto"), "Controller must use BookRequestDto for write operations");
        assertTrue(ctrl.contains("mapper.toEntity"), "Controller must call mapper.toEntity for write ops");
    }

    @Test
    void versionFieldExcludedFromRequestDto() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.Book",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.*;
                import jakarta.persistence.*;
                @Entity
                @ExposeEntity(path="books")
                public class Book {
                    @Id @GeneratedValue private Long id;
                    @Version private Long version;
                    private String title;
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                    public Long getVersion() { return version; }
                    public void setVersion(Long v) { this.version = v; }
                    public String getTitle() { return title; }
                    public void setTitle(String t) { this.title = t; }
                }
                """
            )
        );
        assertThat(c).succeeded();
        String reqDto = c.generatedSourceFile("com.example.entity.generated.BookRequestDto")
            .orElseThrow().getCharContent(false).toString();
        assertFalse(reqDto.contains("version"),
            "RequestDto must NOT contain @Version field — it is managed by JPA");
        assertTrue(reqDto.contains("title"), "RequestDto must still contain non-version fields");
    }

    @Test
    void mapperUpdatesEntityMethodGenerated() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(BOOK_SOURCE);

        assertThat(c).succeeded();
        String mapper = c.generatedSourceFile("com.example.entity.generated.BookMapper")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(mapper.contains("updateEntity"), "Mapper must have updateEntity method for safe PUT merge");
        assertTrue(mapper.contains("MappingTarget"), "updateEntity must use @MappingTarget");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // @ExposeDocument tests
    // ══════════════════════════════════════════════════════════════════════════

    private static final javax.tools.JavaFileObject NOTE_DOCUMENT_SOURCE =
        JavaFileObjects.forSourceString("com.example.document.Note",
            """
            package com.example.document;
            import io.github.notablogger.springxpose.annotation.ExposeDocument;
            import org.springframework.data.annotation.Id;
            import org.springframework.data.mongodb.core.mapping.Document;
            @Document(collection = "notes")
            @ExposeDocument(path = "notes")
            public class Note {
                @Id private String id;
                private String title;
                private String content;
                public String getId()              { return id; }
                public void   setId(String id)     { this.id = id; }
                public String getTitle()           { return title; }
                public void   setTitle(String t)   { this.title = t; }
                public String getContent()         { return content; }
                public void   setContent(String c) { this.content = c; }
            }
            """
        );

    @Test
    void exposeDocument_generatesAllSixArtifacts() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(NOTE_DOCUMENT_SOURCE);

        assertThat(c).succeeded();
        assertThat(c).generatedSourceFile("com.example.document.generated.NoteRepository");
        assertThat(c).generatedSourceFile("com.example.document.generated.NoteDto");
        assertThat(c).generatedSourceFile("com.example.document.generated.NoteRequestDto");
        assertThat(c).generatedSourceFile("com.example.document.generated.NoteMapper");
        assertThat(c).generatedSourceFile("com.example.document.generated.NoteController");
        assertThat(c).generatedSourceFile("com.example.document.generated.NoteSecurityConfigurer");
    }

    @Test
    void exposeDocument_repositoryExtendsMongoRepository() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(NOTE_DOCUMENT_SOURCE);

        assertThat(c).succeeded();
        String repo = c.generatedSourceFile("com.example.document.generated.NoteRepository")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(repo.contains("MongoRepository"), "Repository must extend MongoRepository for @ExposeDocument");
        assertFalse(repo.contains("JpaRepository"),  "Repository must NOT extend JpaRepository for @ExposeDocument");
    }

    @Test
    void exposeDocument_controllerHasNoEntityManager() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(NOTE_DOCUMENT_SOURCE);

        assertThat(c).succeeded();
        String ctrl = c.generatedSourceFile("com.example.document.generated.NoteController")
            .orElseThrow().getCharContent(false).toString();
        assertFalse(ctrl.contains("EntityManager"),
            "Controller must NOT inject EntityManager for @ExposeDocument (no JPA)");
    }

    @Test
    void exposeDocument_controllerHasNoTransactional() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(NOTE_DOCUMENT_SOURCE);

        assertThat(c).succeeded();
        String ctrl = c.generatedSourceFile("com.example.document.generated.NoteController")
            .orElseThrow().getCharContent(false).toString();
        assertFalse(ctrl.contains("@Transactional"),
            "Controller must NOT emit @Transactional for @ExposeDocument (MongoDB is auto-commit)");
    }

    @Test
    void exposeDocument_dtoContainsScalarFields() throws Exception {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor())
            .compile(NOTE_DOCUMENT_SOURCE);

        assertThat(c).succeeded();
        String dto = c.generatedSourceFile("com.example.document.generated.NoteDto")
            .orElseThrow().getCharContent(false).toString();
        assertTrue(dto.contains("title"),   "Dto must contain 'title'");
        assertTrue(dto.contains("content"), "Dto must contain 'content'");
    }

    @Test
    void exposeDocument_failsWhenNoIdField() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.document.NoIdDoc",
                """
                package com.example.document;
                import io.github.notablogger.springxpose.annotation.ExposeDocument;
                import org.springframework.data.mongodb.core.mapping.Document;
                @Document(collection = "noids")
                @ExposeDocument(path = "noids")
                public class NoIdDoc {
                    private String name;
                    public String getName() { return name; }
                    public void setName(String n) { this.name = n; }
                }
                """
            )
        );
        assertFalse(c.status() == Compilation.Status.SUCCESS,
            "Should fail: @ExposeDocument on a class with no @Id field");
        assertTrue(
            c.diagnostics().stream().anyMatch(d ->
                d.getKind() == Diagnostic.Kind.ERROR &&
                d.getMessage(null).contains("@ExposeDocument: no @Id field found")),
            "Expected @ExposeDocument no @Id error not found");
    }

    @Test
    void exposeDocument_warnsWhenUsedWithJpaEntityAnnotation() {
        Compilation c = javac().withProcessors(new ExposeEntityProcessor()).compile(
            JavaFileObjects.forSourceString("com.example.entity.ConfusedEntity",
                """
                package com.example.entity;
                import io.github.notablogger.springxpose.annotation.ExposeDocument;
                import jakarta.persistence.*;
                @Entity
                @ExposeDocument(path = "confused")
                public class ConfusedEntity {
                    @Id @GeneratedValue private Long id;
                    private String name;
                    public Long getId()           { return id; }
                    public void setId(Long id)    { this.id = id; }
                    public String getName()       { return name; }
                    public void setName(String n) { this.name = n; }
                }
                """
            )
        );
        // Compilation may still succeed (it just generates for MONGO) but must warn
        assertTrue(
            c.diagnostics().stream().anyMatch(d ->
                d.getKind() == Diagnostic.Kind.WARNING &&
                d.getMessage(null).contains("@ExposeDocument is intended for MongoDB documents")),
            "Expected WARNING about @ExposeDocument on a JPA @Entity not found");
    }
}
