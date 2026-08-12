package com.darkaxt.dualdex.display;

interface IThorFocusPrivilegedService {
    int readMode() = 1;
    boolean writeMode(int mode) = 2;

    // Shizuku UserService destroy transaction. The generated Binder transaction is 16777115.
    void destroy() = 16777114;
}
