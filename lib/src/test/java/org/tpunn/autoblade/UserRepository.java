package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Create;
import org.tpunn.autoblade.annotations.Lookup;
import org.tpunn.autoblade.annotations.Repository;

@Repository
public interface UserRepository {
    @Create
    UserBlade create(User user);

    @Lookup
    UserBlade get(String userId);
}