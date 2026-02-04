package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Scoped;

import javax.inject.Inject;

@Scoped
@Anchored("User")
public class UserProfileService implements UserProfile {
    private final UserData data;

    @Inject
    public UserProfileService(UserData data) {
        this.data = data;
    }

    @Override
    public String getDisplayInfo() {
        return "User: " + data.username() + " (" + data.userId() + ")";
    }
}
