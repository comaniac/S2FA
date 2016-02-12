package org.apache.j2fa.Annotation;

import java.lang.annotation.*;

public @interface J2FA_Kernel {
	String kernel_type() default "map";
  String output_format() default "Merlin";
}
