package org.tpunn.autoblade.utilities;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

public class ProvisionMethod {
    public static MethodSpec generateProvisionMethod(TypeElement te) {
        TypeName returnType;
        String name;
        if (!te.getInterfaces().isEmpty()) {
            TypeMirror businessInterface = te.getInterfaces().stream()
                    .filter(i -> !i.toString().startsWith("java.lang") && !i.toString().startsWith("java.io"))
                    .map(i -> (TypeMirror) i).findFirst().orElse(te.getInterfaces().get(0));
            returnType = TypeName.get(businessInterface);
            String full = businessInterface.toString();
            name = full.substring(full.lastIndexOf('.') + 1);
        } else {
            returnType = TypeName.get(te.asType());
            name = te.getSimpleName().toString();
        }
        return MethodSpec.methodBuilder("get" + name)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(returnType).build();
    }
}
