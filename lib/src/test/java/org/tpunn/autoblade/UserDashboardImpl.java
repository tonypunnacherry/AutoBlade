package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Transient;

import javax.inject.Inject;

@Transient
@Anchored("User")
public class UserDashboardImpl implements UserDashboard {
    private final UserProfile userProfile;
    private final User userData;

    @Inject
    public UserDashboardImpl(UserProfile userProfile, User userData) {
        this.userProfile = userProfile;
        this.userData = userData;
    }

    public void display() {
        System.out.println("Welcome, " + userData.username() + "!");
        System.out.println(userProfile.getDisplayInfo());
    }
}