package jenkins.plugins.jclouds.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jclouds.domain.Location;

import hudson.util.ListBoxModel;

public class LocationHelper {

    public static void fillLocations(final ListBoxModel m, final Set<? extends Location> locset) {
        List<Location> locations = new ArrayList<>(locset);
        Collections.sort(locations, new Comparator<Location>() {
            @Override
            public int compare(Location o1, Location o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });
        for (Location loc : locations) {
            m.add(String.format("%s (%s)", loc.getId(), loc.getDescription()), loc.getId());
        }
    }
}
