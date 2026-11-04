/*
 * COMPILE-ONLY STUB. Mirrors org.apache.jmeter.gui.plugin.MenuCreator from ApacheJMeter_core
 * so the plugin can be compiled offline by build.sh. It is NEVER packaged into the plugin jar;
 * at runtime JMeter's own class is used. The Maven build uses the real JMeter dependency instead.
 */
package org.apache.jmeter.gui.plugin;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;

public interface MenuCreator {
    enum MENU_LOCATION { FILE, EDIT, RUN, OPTIONS, HELP, SEARCH, TOOLS }

    JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location);

    JMenu[] getTopLevelMenus();

    boolean localeChanged(MenuElement menu);

    void localeChanged();
}
