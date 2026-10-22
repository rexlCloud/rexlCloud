package de.rexlcloud.core.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.io.Serializable;
import java.text.DecimalFormat;

/**
 * A wrapper (a machine that runs servers) as the API sees it, with the resource usage it last reported.
 */
@Getter
@AllArgsConstructor
public class Wrapper implements Serializable {

    private static final float memoryFactor = 1024 * 1024 * 1024;
    private static final DecimalFormat formatter = new DecimalFormat("0.00");
    /**
     * Name of the wrapper, e.g. {@code Wrapper-1}.
     */
    @NonNull
    private final String name;
    /**
     * Whether the wrapper is connected to the master.
     */
    private boolean online;
    /**
     * CPU load of the wrapper's machine from 0 to 1; negative if unknown.
     */
    private double cpuUsage;
    /**
     * Memory of the machine in bytes: in use, available to new servers (including the page cache the system gives
     * back) and in total.
     */
    private long usedMemory, freeMemory, totalMemory;

    /**
     * Internal: applies a newer state of the wrapper.
     */
    protected void update(Wrapper wrapper) {
        this.online = wrapper.isOnline();
        this.cpuUsage = wrapper.getCpuUsage();
        this.usedMemory = wrapper.getUsedMemory();
        this.freeMemory = wrapper.getFreeMemory();
        this.totalMemory = wrapper.getTotalMemory();
    }

    /**
     * @return the CPU load as text, e.g. {@code 12.50%}
     */
    public String getCpuUsagePercent() {
        return formatter.format(cpuUsage * 100d) + "%";
    }

    /**
     * @return the total memory in GB as text, e.g. {@code 15.62}
     */
    public String getTotalMemoryString() {
        return formatter.format(totalMemory / memoryFactor);
    }

    /**
     * @return the memory in use in GB as text
     */
    public String getUsedMemoryString() {
        return formatter.format(usedMemory / memoryFactor);
    }

    /**
     * @return the available memory in GB as text
     */
    public String getFreeMemoryString() {
        return formatter.format(freeMemory / memoryFactor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        var wrapper = (Wrapper) o;

        if (online != wrapper.online) return false;
        if (Double.compare(wrapper.cpuUsage, cpuUsage) != 0) return false;
        if (usedMemory != wrapper.usedMemory) return false;
        if (freeMemory != wrapper.freeMemory) return false;
        if (totalMemory != wrapper.totalMemory) return false;
        return name.equals(wrapper.name);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = name.hashCode();
        result = 31 * result + (online ? 1 : 0);
        temp = Double.doubleToLongBits(cpuUsage);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (usedMemory ^ (usedMemory >>> 32));
        result = 31 * result + (int) (freeMemory ^ (freeMemory >>> 32));
        result = 31 * result + (int) (totalMemory ^ (totalMemory >>> 32));
        return result;
    }
}
