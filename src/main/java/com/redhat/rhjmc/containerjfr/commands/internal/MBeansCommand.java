package com.redhat.rhjmc.containerjfr.commands.internal;

import com.redhat.rhjmc.containerjfr.tui.ClientWriter;
import com.sun.management.OperatingSystemMXBean;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.ObjectName;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Method;

@Singleton
class MBeansCommand extends AbstractConnectedCommand {

    private final ClientWriter cw;

    @Inject
    MBeansCommand(ClientWriter cw) {
        this.cw = cw;
    }

    @Override
    public String getName() {
        return "mbeans";
    }

    @Override
    public void execute(String[] args) throws Exception {
        printMBeanInfo(ObjectName.getInstance("java.lang", "type", "OperatingSystem"), OperatingSystemMXBean.class);
        printMBeanInfo(ObjectName.getInstance("java.lang", "type", "Memory"), MemoryMXBean.class);
    }

    private <T> void printMBeanInfo(ObjectName on, Class<T> klazz) throws Exception {
        T bean = getConnection().getMBean(on, klazz);
        cw.println(klazz.getSimpleName() + ":");
        for (Method method : klazz.getMethods()) {
            if (method.getName().matches("get[\\w]+") && method.getParameterTypes().length == 0) {
                cw.println(String.format("\t%s(): %s", method.getName(), method.invoke(bean).toString()));
            }
        }
    }

    @Override
    public boolean validate(String[] args) {
        return true;
    }

}