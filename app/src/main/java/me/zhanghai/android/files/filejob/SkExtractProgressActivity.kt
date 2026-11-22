/*
 * 白い熊 fork: the translucent host of the extraction progress dialog. It is its own activity —
 * like the conflict and error dialogs of a file job — so the dialog can be opened both from the
 * file list (right after Paste) and from the job's notification, whatever is on screen then.
 */

package me.zhanghai.android.files.filejob

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.createIntent

class SkExtractProgressActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(
                    SkExtractProgressDialogFragment.newInstance(
                        intent.getIntExtra(EXTRA_JOB_ID, 0)
                    ), FRAGMENT_TAG
                )
            }
        }
    }

    // A second extraction's notification was tapped while this dialog is up: show that job.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val jobId = intent.getIntExtra(EXTRA_JOB_ID, 0)
        if (jobId == this.intent.getIntExtra(EXTRA_JOB_ID, 0)) {
            return
        }
        setIntent(intent)
        val oldFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
            as? SkExtractProgressDialogFragment
        supportFragmentManager.commitNow {
            if (oldFragment != null) {
                // Its dismissal must not close the activity that the new dialog is about to use.
                oldFragment.isReplaced = true
                remove(oldFragment)
            }
            add(SkExtractProgressDialogFragment.newInstance(jobId), FRAGMENT_TAG)
        }
    }

    companion object {
        private const val EXTRA_JOB_ID = "jobId"

        private const val FRAGMENT_TAG = "SkExtractProgressDialogFragment"

        fun createIntent(jobId: Int): Intent =
            SkExtractProgressActivity::class.createIntent()
                .putExtra(EXTRA_JOB_ID, jobId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
