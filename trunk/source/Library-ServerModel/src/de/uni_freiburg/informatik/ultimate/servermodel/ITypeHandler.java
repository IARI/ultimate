package de.uni_freiburg.informatik.ultimate.servermodel;

public interface ITypeHandler<T> {
	public void consume(T data);

	public T supply();

	public <D> T supply(Class<D> argType, D data);
}