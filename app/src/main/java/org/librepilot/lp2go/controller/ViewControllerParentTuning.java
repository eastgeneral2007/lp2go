/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.librepilot.lp2go.controller;

import android.view.View;

import org.librepilot.lp2go.MainActivity;
import org.librepilot.lp2go.R;
import org.librepilot.lp2go.helper.SettingsHelper;

import java.util.Set;

public class ViewControllerParentTuning extends ViewController {

    public ViewControllerParentTuning(MainActivity activity, int title, int icon, int localSettingsVisible, int flightSettingsVisible) {
        super(activity, title, icon, localSettingsVisible, flightSettingsVisible);

        final MainActivity ma = getMainActivity();

        ma.mViews.put(VIEW_P_TUNING, ma.getLayoutInflater().inflate(R.layout.activity_parent_tuning, null));
        ma.setContentView(ma.mViews.get(VIEW_P_TUNING));
        initRightMenu();
    }

    @Override
    public void initRightMenu() {
        final MainActivity ma = getMainActivity();
        ViewController mVcPid = new ViewControllerPid(ma, R.string.menu_pid,
                R.drawable.ic_tune_128dp, View.VISIBLE, View.INVISIBLE);
        ViewController mVcVPid = new ViewControllerVerticalPid(ma, R.string.menu_vpid,
                R.drawable.ic_vertical_align_center_black_128dp, View.VISIBLE, View.INVISIBLE);

        mRightMenuItems.put(ViewController.VIEW_PID, mVcPid);
        mRightMenuItems.put(ViewController.VIEW_VPID, mVcVPid);

        Set<String> favSet = SettingsHelper.mRightMenuFavorites;
        boolean set = false;
        for (ViewController v : mRightMenuItems.values()) {
            if (favSet.contains(String.valueOf(v.getID()))) {
                setFavorite(v.getID());
                set = true;
            }
        }
        if (!set) {
            setFavorite(ViewController.VIEW_PID);
        }
    }

    @Override
    public int getID() {
        return ViewController.VIEW_P_TUNING;
    }

    @Override
    public void enter(int view) {
        super.enter(view);
        getMainActivity().displayRightMenuView(getFavorite().getID());
    }
}
