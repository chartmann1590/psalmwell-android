package com.hartmannstudios.psalmwell

import android.content.Context
import com.hartmannstudios.psalmwell.data.AuthRepository
import com.hartmannstudios.psalmwell.data.CommunityRepository
import com.hartmannstudios.psalmwell.data.LocalStore
import com.hartmannstudios.psalmwell.data.ScriptureRepository

class AppContainer(context: Context) {
    val scripture = ScriptureRepository(context)
    val store = LocalStore(context)
    val auth = AuthRepository(context)
    val community = CommunityRepository(auth)
}
