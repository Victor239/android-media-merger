package com.github.axet.mover.widgets;

import android.content.Context;

import com.github.axet.androidlibrary.app.SuperUser;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;

import java.io.File;

public class OpenFileDialogSuperUser extends OpenFileDialog {
    public OpenFileDialogSuperUser(Context context, DIALOG_TYPE type) {
        super(context, type);
    }

    @Override
    protected boolean canWrite(File p) {
        boolean b = super.canWrite(p);
        if (!b && SuperUser.isRooted()) {
            try {
                return SuperUser.touch(p);
            } catch (RuntimeException e) {
                return false;
            }
        }
        return b;
    }

    @Override
    protected boolean mkdirs(File f) {
        boolean b = super.mkdirs(f);
        if (b || !SuperUser.isRooted())
            return b;
        if (f.exists())
            return true;
        try {
            return SuperUser.mkdirs(f);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    protected boolean delete(File f) {
        boolean b = super.delete(f);
        if (b || !SuperUser.isRooted())
            return b;
        try {
            return SuperUser.delete(f);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
