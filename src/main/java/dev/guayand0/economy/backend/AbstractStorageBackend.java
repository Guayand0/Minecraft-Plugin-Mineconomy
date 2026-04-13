package dev.guayand0.economy.backend;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractStorageBackend implements StorageBackend {

    protected static final String BALANCE_PATH = "balance";
    protected static final String PLAYER_NAME_PATH = "player_name";

    protected final Plugin plugin;
    protected final File rootFolder;
    private final AtomicLong totalStorageOperationNanos = new AtomicLong();
    private final AtomicLong totalStorageOperationCount = new AtomicLong();
    private final AtomicLong readStorageOperationNanos = new AtomicLong();
    private final AtomicLong readStorageOperationCount = new AtomicLong();
    private final AtomicLong writeStorageOperationNanos = new AtomicLong();
    private final AtomicLong writeStorageOperationCount = new AtomicLong();

    protected AbstractStorageBackend(Plugin plugin) {
        this.plugin = plugin;
        this.rootFolder = new File(plugin.getDataFolder(), "player_economy");
        ensureFolderExists(rootFolder);
    }

    @Override
    public final boolean hasAccount(UUID uuid) {
        long startedAt = System.nanoTime();
        try {
            return doHasAccount(uuid);
        } finally {
            recordReadStorageOperation(startedAt);
        }
    }

    @Override
    public final boolean createAccount(UUID uuid) {
        long startedAt = System.nanoTime();
        try {
            return doCreateAccount(uuid);
        } finally {
            recordWriteStorageOperation(startedAt);
        }
    }

    @Override
    public final void updatePlayerName(UUID uuid, String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return;
        }

        long startedAt = System.nanoTime();
        try {
            doUpdatePlayerName(uuid, playerName);
        } finally {
            recordWriteStorageOperation(startedAt);
        }
    }

    @Override
    public final double getBalance(UUID uuid) {
        long startedAt = System.nanoTime();
        try {
            return doGetBalance(uuid);
        } finally {
            recordReadStorageOperation(startedAt);
        }
    }

    @Override
    public final void setBalance(UUID uuid, double amount) {
        long startedAt = System.nanoTime();
        try {
            doSetBalance(uuid, Math.max(0.0D, amount));
        } finally {
            recordWriteStorageOperation(startedAt);
        }
    }

    @Override
    public final List<AccountBalance> getTopBalances(int limit) {
        long startedAt = System.nanoTime();
        try {
            return doGetTopBalances(limit);
        } finally {
            recordReadStorageOperation(startedAt);
        }
    }

    @Override
    public final List<AccountBalance> getRegisteredAccounts() {
        long startedAt = System.nanoTime();
        try {
            return doGetRegisteredAccounts();
        } finally {
            recordReadStorageOperation(startedAt);
        }
    }

    @Override
    public final int getRegisteredAccountCount() {
        long startedAt = System.nanoTime();
        try {
            return doGetRegisteredAccountCount();
        } finally {
            recordReadStorageOperation(startedAt);
        }
    }

    @Override
    public double getTotalStorageAverageMillis() {
        long count = totalStorageOperationCount.get();
        if (count <= 0L) {
            return 0.0D;
        }

        return nanosToMillis(totalStorageOperationNanos.get()) / count;
    }

    @Override
    public long getTotalStorageOperationCount() {
        return totalStorageOperationCount.get();
    }

    @Override
    public double getReadStorageAverageMillis() {
        long count = readStorageOperationCount.get();
        if (count <= 0L) {
            return 0.0D;
        }

        return nanosToMillis(readStorageOperationNanos.get()) / count;
    }

    @Override
    public long getReadStorageOperationCount() {
        return readStorageOperationCount.get();
    }

    @Override
    public double getWriteStorageAverageMillis() {
        long count = writeStorageOperationCount.get();
        if (count <= 0L) {
            return 0.0D;
        }

        return nanosToMillis(writeStorageOperationNanos.get()) / count;
    }

    @Override
    public long getWriteStorageOperationCount() {
        return writeStorageOperationCount.get();
    }

    protected File getTypeFolder() {
        return new File(rootFolder, getType().name());
    }

    protected void ensureFolderExists(File folder) {
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private void recordReadStorageOperation(long startedAt) {
        long elapsedNanos = System.nanoTime() - startedAt;
        totalStorageOperationNanos.addAndGet(elapsedNanos);
        totalStorageOperationCount.incrementAndGet();
        readStorageOperationNanos.addAndGet(elapsedNanos);
        readStorageOperationCount.incrementAndGet();
    }

    private void recordWriteStorageOperation(long startedAt) {
        long elapsedNanos = System.nanoTime() - startedAt;
        totalStorageOperationNanos.addAndGet(elapsedNanos);
        totalStorageOperationCount.incrementAndGet();
        writeStorageOperationNanos.addAndGet(elapsedNanos);
        writeStorageOperationCount.incrementAndGet();
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    protected abstract boolean doHasAccount(UUID uuid);

    protected abstract boolean doCreateAccount(UUID uuid);

    protected abstract double doGetBalance(UUID uuid);

    protected abstract void doSetBalance(UUID uuid, double amount);

    protected abstract void doUpdatePlayerName(UUID uuid, String playerName);

    protected List<AccountBalance> doGetRegisteredAccounts() {
        return doGetTopBalances(Integer.MAX_VALUE);
    }

    protected int doGetRegisteredAccountCount() {
        return doGetRegisteredAccounts().size();
    }

    protected abstract List<AccountBalance> doGetTopBalances(int limit);
}
