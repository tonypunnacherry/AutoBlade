package org.tpunn.autoblade;

import com.google.auto.service.AutoService;
import org.tpunn.autoblade.annotations.*;
import org.tpunn.autoblade.generators.*;
import org.tpunn.autoblade.validators.Validator;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "org.tpunn.autoblade.annotations.Anchor",
        "org.tpunn.autoblade.annotations.Seed",
        "org.tpunn.autoblade.annotations.Scoped",
        "org.tpunn.autoblade.annotations.EntryPoint"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class AutoBladeProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        new AnchorGenerator(processingEnv).generate(roundEnv);
        new Validator(processingEnv).validate(roundEnv);
        new BindingGenerator(processingEnv).generate(roundEnv);
        new ScopeGenerator(processingEnv).generate(roundEnv);
        new EntryPointGenerator(processingEnv).generate(roundEnv);

        return true;
    }
}
