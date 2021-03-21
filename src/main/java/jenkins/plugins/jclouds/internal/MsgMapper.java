package jenkins.plugins.jclouds.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import org.jvnet.localizer.Localizable;
import jenkins.plugins.jclouds.compute.Messages;

public class MsgMapper implements org.kohsuke.args4j.Localizable {
    private final String mname;
    private final Method m;
    private final int n;

    public MsgMapper(final Method mth) {
        mname = mth.getName();
        m = mth;
        n = 0;
    }

    public MsgMapper(final Class<Messages> clazz, final String msgId) {
        mname = msgId;
        final Class<Object> ocl = Object.class;
        Method tmpm = null;
        int nargs = 0;
        try {
            tmpm = clazz.getMethod(msgId);
        } catch (NoSuchMethodException e) {
            try {
                tmpm = clazz.getMethod(msgId, ocl);
                nargs++;
            } catch (NoSuchMethodException eq) {
                try {
                    tmpm = clazz.getMethod(msgId, ocl, ocl);
                    nargs++;
                } catch (NoSuchMethodException e2) {
                    try {
                        tmpm = clazz.getMethod(msgId, ocl, ocl, ocl);
                        nargs++;
                    } catch (NoSuchMethodException e3) {
                        try {
                            tmpm = clazz.getMethod(msgId, ocl, ocl, ocl, ocl);
                            nargs++;
                        } catch (NoSuchMethodException e4) {
                            try {
                                tmpm = clazz.getMethod(msgId, ocl, ocl, ocl, ocl, ocl);
                                nargs++;
                            } catch (NoSuchMethodException e5) {
                                throw new RuntimeException("Could not find method " + msgId);
                            }
                        }
                    }
                }
            }
        }
        m = tmpm;
        n = nargs;
    }

    public String formatWithLocale(Locale locale, Object... args) {
        if (args.length < n) {
            throw new IllegalArgumentException("Not enough arguments");
        }
        Localizable l;
        try {
            switch (n) {
                case 0:
                    l = (Localizable)m.invoke(this);
                    return l.toString(locale);
                case 1:
                    l = (Localizable)m.invoke(this, args[0]);
                    return l.toString(locale);
                case 2:
                    l = (Localizable)m.invoke(this, args[0], args[1]);
                    return l.toString(locale);
                case 3:
                    l = (Localizable)m.invoke(this, args[0], args[1], args[2]);
                    return l.toString(locale);
                case 4:
                    l = (Localizable)m.invoke(this, args[0], args[1], args[2], args[3]);
                    return l.toString(locale);
                default:
                    l = (Localizable)m.invoke(this, args[0], args[1], args[2], args[3], args[4]);
                    return l.toString(locale);
            }
        } catch (IllegalAccessException|InvocationTargetException e) {
            throw new RuntimeException("Could not access method " + mname);
        }
    }

    public String format(Object... args) {
        return formatWithLocale(Locale.getDefault(), args);
    }
}
