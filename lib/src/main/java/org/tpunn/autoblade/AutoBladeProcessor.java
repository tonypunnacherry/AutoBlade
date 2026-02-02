package org.tpunn.autoblade;

import com.google.auto.service.AutoService;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * The central controller for AutoBlade.
 * Orchestrates the generation of AppComponents, Subcomponents, and Services.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
    "org.tpunn.autoblade.Service",
    "org.tpunn.autoblade.EntryPoint",
    "org.tpunn.autoblade.Seed",
    "javax.inject.Singleton"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class AutoBladeProcessor extends AbstractProcessor {

    private BindingGenerator bindingGenerator;
    private EntryPointGenerator entryPointGenerator;
    private ScopeGenerator scopeGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        // Initialize the specialized generation logic
        this.bindingGenerator = new BindingGenerator(processingEnv);
        this.entryPointGenerator = new EntryPointGenerator(processingEnv);
        this.scopeGenerator = new ScopeGenerator(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // If the compiler is done with this round, stop to avoid duplicate file errors
        if (roundEnv.processingOver()) {
            return false;
        }

        // 1. Generate the sorted Dagger Modules (AppAutoModule, UserAutoModule, etc.)
        bindingGenerator.generate(roundEnv);

        // 2. Generate the Scoped Subcomponents and their ManagerServices
        // Note: This needs the roundEnv to find scoped EntryPoints
        scopeGenerator.generate(roundEnv);

        // 3. Generate the master AppComponent
        // This always runs to ensure the root of the graph is present
        entryPointGenerator.generate(roundEnv);

        // Returning false allows Dagger 2.59 to process the same classes 
        // after our files have been written to the filesystem.
        return false;
    }
}