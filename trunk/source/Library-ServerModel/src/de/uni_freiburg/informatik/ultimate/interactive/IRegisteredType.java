package de.uni_freiburg.informatik.ultimate.interactive;

public interface IRegisteredType<T> {
	public Class<T> getType();

	public T getDefaultInstance();

	public String registeredName();
}