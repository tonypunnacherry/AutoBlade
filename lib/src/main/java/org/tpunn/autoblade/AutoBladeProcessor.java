package org.tpunn.autoblade;

import com.google.auto.service.AutoService;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import org.tpunn.autoblade.processors.BindingProcessor;
import org.tpunn.autoblade.processors.ComponentProcessor;
import org.tpunn.autoblade.processors.RepositoryProcessor;

import java.util.Set;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
    "org.tpunn.autoblade.annotations.Repository",
    "org.tpunn.autoblade.annotations.Blade",
    "org.tpunn.autoblade.annotations.Seed",
    "org.tpunn.autoblade.annotations.Scoped",
    "org.tpunn.autoblade.annotations.Transient"
})
public class AutoBladeProcessor extends AbstractProcessor {

    private RepositoryProcessor repositoryProcessor;
    private ComponentProcessor componentProcessor;
    private BindingProcessor bindingProcessor;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        // Initialize your existing classes
        this.repositoryProcessor = new RepositoryProcessor();
        this.componentProcessor = new ComponentProcessor();
        this.bindingProcessor = new BindingProcessor();

        // Pass the environment down to the individuals
        this.repositoryProcessor.init(processingEnv);
        this.componentProcessor.init(processingEnv);
        this.bindingProcessor.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        // STEP 1: Generate the concrete implementations (_Repo.java)
        // This ensures the files exist for the compiler's symbol table immediately.
        repositoryProcessor.process(annotations, roundEnv);

        // STEP 2: Generate the Component interfaces (AppBlade, UserBlade)
        // These provide the Builder methods needed by the Repos.
        componentProcessor.process(annotations, roundEnv);

        // STEP 3: Generate the Dagger Modules (@Binds)
        // This MUST happen after Repos are generated so Dagger sees the symbols.
        bindingProcessor.process(annotations, roundEnv);

        return true;
    }

}