package org.apache.j2fa.Annotation;

import java.lang.annotation.*;

@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface J2FA_Kernel {
  String kernel();
}
