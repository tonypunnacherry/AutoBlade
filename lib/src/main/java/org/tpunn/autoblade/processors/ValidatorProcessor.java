package org.tpunn.autoblade.processors;

import com.google.auto.service.AutoService;
import org.tpunn.autoblade.validators.Validator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
    "org.tpunn.autoblade.annotations.Scoped",
    "org.tpunn.autoblade.annotations.EntryPoint",
    "org.tpunn.autoblade.annotations.Seed"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ValidatorProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;
        new Validator(processingEnv).validate(roundEnv);
        return true;
    }
}
