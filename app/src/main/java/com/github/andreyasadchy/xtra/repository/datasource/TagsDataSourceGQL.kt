package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import kotlinx.coroutines.CoroutineScope

class TagsDataSourceGQL private constructor(
    private val clientId: String?,
    private val getGameTags: Boolean,
    private val query: String?,
    private val api: GraphQLRepository,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Tag>(coroutineScope) {

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Tag>) {
        loadInitial(params, callback) {
            if (getGameTags) {
                val get = api.loadGameTags(clientId, query)
                get.data.ifEmpty { listOf() }
            } else {
                if (!query.isNullOrBlank()) {
                    val get = api.loadFreeformTags(clientId, query)
                    get.data.ifEmpty { listOf() }
                } else listOf()
            }
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Tag>) {
        loadRange(params, callback) {
            listOf()
        }
    }

    class Factory(
        private val clientId: String?,
        private val getGameTags: Boolean,
        private val query: String?,
        private val api: GraphQLRepository,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Tag, TagsDataSourceGQL>() {

        override fun create(): DataSource<Int, Tag> =
                TagsDataSourceGQL(clientId, getGameTags, query, api, coroutineScope).also(sourceLiveData::postValue)
    }
}
