@file:Suppress("DEPRECATION")

package io.legado.app.ui.book.toc

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import com.google.android.material.tabs.TabLayout
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityChapterListBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.utils.applyTint
import io.legado.app.utils.gone
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible


class TocActivity : VMBaseActivity<ActivityChapterListBinding, TocViewModel>() {

    override val binding by viewBinding(ActivityChapterListBinding::inflate)
    override val viewModel by viewModels<TocViewModel>()

    private lateinit var tabLayout: TabLayout
    private var searchView: SearchView? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        tabLayout = binding.titleBar.findViewById(R.id.tab_layout)
        tabLayout.isTabIndicatorFullWidth = false
        tabLayout.setSelectedTabIndicatorColor(accentColor)
        binding.viewPager.adapter = TabFragmentPageAdapter()
        tabLayout.setupWithViewPager(binding.viewPager)
        intent.getStringExtra("bookUrl")?.let {
            viewModel.initBook(it)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_toc, menu)
        val search = menu.findItem(R.id.menu_search)
        searchView = (search.actionView as SearchView).apply {
            applyTint(primaryTextColor)
            maxWidth = resources.displayMetrics.widthPixels
            onActionViewCollapsed()
            setOnCloseListener {
                tabLayout.visible()
                false
            }
            setOnSearchClickListener { tabLayout.gone() }
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    if (tabLayout.selectedTabPosition == 1) {
                        viewModel.startBookmarkSearch(newText)
                    } else {
                        viewModel.startChapterListSearch(newText)
                    }
                    return false
                }
            })
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_use_replace)?.isChecked = AppConfig.tocUiUseReplace
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reverse_toc -> viewModel.reverseToc {
                viewModel.chapterListCallBack?.upChapterList(searchView?.query?.toString())
                setResult(RESULT_OK, Intent().apply {
                    putExtra("index", it.durChapterIndex)
                    putExtra("chapterPos", 0)
                })
            }
            R.id.menu_use_replace -> {
                AppConfig.tocUiUseReplace = !item.isChecked
                viewModel.chapterListCallBack?.clearDisplayTitle()
                viewModel.chapterListCallBack?.upChapterList(searchView?.query?.toString())
            }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (tabLayout.isGone) {
            searchView?.onActionViewCollapsed()
            tabLayout.visible()
        } else {
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter :
        FragmentPagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int): Fragment {
            return when (position) {
                1 -> BookmarkFragment()
                else -> ChapterListFragment()
            }
        }

        override fun getCount(): Int {
            return 2
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                1 -> getString(R.string.bookmark)
                else -> getString(R.string.chapter_list)
            }
        }

    }

}