package net.sadness.playerstats.storage;

@FunctionalInterface
public interface StorageTask<T> {

    T run() throws StorageException;
}
