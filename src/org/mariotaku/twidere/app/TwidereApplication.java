/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.app;

import static android.os.Environment.getExternalStorageDirectory;

import java.io.File;
import java.io.FileFilter;

import org.mariotaku.twidere.Constants;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.util.AsyncTaskManager;
import org.mariotaku.twidere.util.GetExternalCacheDirAccessor;
import org.mariotaku.twidere.util.ManagedAsyncTask;
import org.mariotaku.twidere.util.MemCache;
import org.mariotaku.twidere.util.ProfileImageLoader;
import org.mariotaku.twidere.util.ServiceInterface;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask.Status;
import android.os.Build;

public class TwidereApplication extends Application implements Constants {

	private ProfileImageLoader mProfileImageLoader;
	private AsyncTaskManager mAsyncTaskManager;
	private ClearCacheTask mClearCacheTask;
	private ServiceInterface mServiceInterface;
	private MemCache mMemCache;

	public void clearCache() {
		if (mClearCacheTask == null || mClearCacheTask.getStatus() == Status.FINISHED) {
			mClearCacheTask = new ClearCacheTask(this, getAsyncTaskManager());
			mClearCacheTask.execute();
		}
	}

	public AsyncTaskManager getAsyncTaskManager() {
		if (mAsyncTaskManager == null) {
			mAsyncTaskManager = new AsyncTaskManager();
		}
		return mAsyncTaskManager;
	}

	public MemCache getMemCache() {
		if (mMemCache == null) {
			mMemCache = MemCache.getInstance();
		}
		return mMemCache;
	}

	public ProfileImageLoader getProfileImageLoader() {
		if (mProfileImageLoader == null) {
			mProfileImageLoader = new ProfileImageLoader(this, R.drawable.ic_profile_image_default, getResources()
					.getDimensionPixelSize(R.dimen.profile_image_size));
		}
		return mProfileImageLoader;
	}

	public ServiceInterface getServiceInterface() {

		if (mServiceInterface == null) {
			mServiceInterface = ServiceInterface.getInstance(this);
		}
		return mServiceInterface;
	}

	@Override
	public void onLowMemory() {
		if (mProfileImageLoader != null) {
			mProfileImageLoader.clearMemoryCache();
		}
		super.onLowMemory();
	}

	private static class ClearCacheTask extends ManagedAsyncTask<Void, Void, Void> {

		private final Context context;

		public ClearCacheTask(Context context, AsyncTaskManager manager) {
			super(context, manager);
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... args) {
			final File external_cache_dir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? GetExternalCacheDirAccessor
					.getExternalCacheDir(context) : getExternalStorageDirectory() != null ? new File(
					getExternalStorageDirectory().getPath() + "/Android/data/" + context.getPackageName() + "/cache/")
					: null;

			if (external_cache_dir != null) {
				for (final File file : external_cache_dir.listFiles((FileFilter) null)) {
					deleteRecursive(file);
				}
			}
			final File internal_cache_dir = context.getCacheDir();
			if (internal_cache_dir != null) {
				for (final File file : internal_cache_dir.listFiles((FileFilter) null)) {
					deleteRecursive(file);
				}
			}
			return null;
		}

		private void deleteRecursive(File f) {
			if (f.isDirectory()) {
				for (final File c : f.listFiles()) {
					deleteRecursive(c);
				}
			}
			f.delete();
		}

	}
}
