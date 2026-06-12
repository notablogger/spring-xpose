package io.github.notablogger.springxpose.processor;

import com.google.auto.service.AutoService;
import io.github.notablogger.springxpose.annotation.ExposeDocument;
import io.github.notablogger.springxpose.annotation.ExposeEntity;
import io.github.notablogger.springxpose.processor.generator.DtoGenerator;
import io.github.notablogger.springxpose.processor.generator.FilterParamsGenerator;
import io.github.notablogger.springxpose.processor.generator.MapperGenerator;
import io.github.notablogger.springxpose.processor.generator.RequestDtoGenerator;
import io.github.notablogger.springxpose.processor.generator.RestControllerGenerator;
import io.github.notablogger.springxpose.processor.generator.RepositoryGenerator;
import io.github.notablogger.springxpose.processor.generator.SecurityConfigurerGenerator;
import io.github.notablogger.springxpose.processor.generator.SpecificationGenerator;
import io.github.notablogger.springxpose.processor.model.EntityModel;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.LinkedHashSet;
import java.util.Set;

@SupportedAnnotationTypes({
    "io.github.notablogger.springxpose.annotation.ExposeEntity",
    "io.github.notablogger.springxpose.annotation.ExposeDocument"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class ExposeEntityProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<Element> allElements = new LinkedHashSet<>();
        allElements.addAll(roundEnv.getElementsAnnotatedWith(ExposeEntity.class));
        allElements.addAll(roundEnv.getElementsAnnotatedWith(ExposeDocument.class));

        for (Element element : allElements) {
            if (element.getKind() != ElementKind.CLASS) continue;

            TypeElement entityClass = (TypeElement) element;
            EntityModel model = EntityModel.parse(entityClass, processingEnv);
            if (model == null) continue;

            try { new RepositoryGenerator(processingEnv).generate(model); }
            catch (Exception e) { error(entityClass, "repository", e); }

            try { new DtoGenerator(processingEnv).generate(model); }
            catch (Exception e) { error(entityClass, "DTO", e); }

            try { new RequestDtoGenerator(processingEnv).generate(model); }
            catch (Exception e) { error(entityClass, "request DTO", e); }

            try { new MapperGenerator(processingEnv).generate(model); }
            catch (Exception e) { error(entityClass, "mapper", e); }

            try { new FilterParamsGenerator(processingEnv).generate(model); }
            catch (Exception e) { error(entityClass, "filter params", e); }

            try { new SpecificationGenerator(processingEnv).generate(model); }
            catch (Exception e) { error(entityClass, "specification", e); }

            try { new RestControllerGenerator(processingEnv).generate(model); }
            catch (Exception e) { error(entityClass, "controller", e); }

            try { new SecurityConfigurerGenerator(processingEnv).generate(model); }
            catch (Exception e) { error(entityClass, "security", e); }
        }
        return true;
    }

    private void error(TypeElement element, String phase, Exception e) {
        processingEnv.getMessager().printMessage(
            javax.tools.Diagnostic.Kind.ERROR,
            "spring-xpose: " + phase + " generation failed for " + element.getSimpleName() + ": " + e,
            element);
    }
}
