package com.amd.aparapi.internal.writer;

public interface ScalaParameter {
	public static enum DIRECTION {
		IN, OUT
	}

	public String getInputParameterString(KernelWriter writer);
	public String getOutputParameterString(KernelWriter writer);
	public String getStructString(KernelWriter writer);
	public String getAssignString(KernelWriter writer);
	public Class<?> getClazz();
	public DIRECTION getDir();
}

