package org.apache.j2fa.Annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface J2FA_Kernel {
  String kernel();
}
