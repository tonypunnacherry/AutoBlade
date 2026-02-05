package org.tpunn.autoblade;

import com.google.auto.service.AutoService;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import org.tpunn.autoblade.processors.*;
import org.tpunn.autoblade.utilities.FileCollector;
import org.tpunn.autoblade.validators.Validator;

import java.util.*;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes({
    "org.tpunn.autoblade.annotations.Repository",
    "org.tpunn.autoblade.annotations.Blade",
    "org.tpunn.autoblade.annotations.Seed",
    "org.tpunn.autoblade.annotations.Scoped",
    "org.tpunn.autoblade.annotations.Transient",
    "org.tpunn.autoblade.annotations.Anchored",
    "org.tpunn.autoblade.annotations.Factory",
    "org.tpunn.autoblade.annotations.Builder"
})
public class AutoBladeProcessor extends AbstractProcessor {

    private RepositoryProcessor repositoryProcessor;
    private ComponentProcessor componentProcessor;
    private BindingProcessor bindingProcessor;
    private AnchorProcessor anchorProcessor;
    private FactoryProcessor factoryProcessor;
    private Validator validator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.repositoryProcessor = new RepositoryProcessor();
        this.componentProcessor = new ComponentProcessor();
        this.bindingProcessor = new BindingProcessor();
        this.anchorProcessor = new AnchorProcessor();
        this.factoryProcessor = new FactoryProcessor();
        this.validator = new Validator(processingEnv);

        this.repositoryProcessor.init(processingEnv);
        this.componentProcessor.init(processingEnv);
        this.bindingProcessor.init(processingEnv);
        this.anchorProcessor.init(processingEnv);
        this.factoryProcessor.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        // Perform a fresh scan each round; use lowercase keys for case-insensitive lookup
        Map<String, TypeElement> anchorMap = FileCollector.mapAnchorsToSeeds(roundEnv);

        this.validator.validate(roundEnv, anchorMap);

        // Inject current round knowledge
        repositoryProcessor.setAnchorMap(anchorMap);

        // Execute sequentially: Repository -> Component -> Anchor -> Binding
        repositoryProcessor.process(annotations, roundEnv);
        factoryProcessor.process(annotations, roundEnv);
        componentProcessor.process(annotations, roundEnv);
        anchorProcessor.process(annotations, roundEnv);
        bindingProcessor.process(annotations, roundEnv);

        return true;
    }
}
