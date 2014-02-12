package fi.allacca

import android.app.{LoaderManager, ListFragment}
import android.database.Cursor
import android.widget._
import scala.Array
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.content.{Context, CursorLoader, ContentUris, Loader}
import android.provider.CalendarContract
import android.util.Log
import android.view.ViewGroup.LayoutParams

class AgendaFragment extends ListFragment with LoaderManager.LoaderCallbacks[Cursor] {
  private val ids = new IdGenerator
  private lazy val activity = getActivity
  private lazy val dimensions = new ScreenParameters(getResources.getDisplayMetrics)

  private val VIEW_ID_TITLE = 1

  private lazy val adapter = new SimpleCursorAdapter(activity, -1, null, Array("_id", "title"), Array(-1), 0) {
    override def newView(context: Context, cursor: Cursor, parent: ViewGroup): View = {
      val linesLayout = new LinearLayout(activity)
      linesLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
      linesLayout.setId(ids.nextId)
      val titleView = new TextView(activity)
      titleView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
      titleView.setId(VIEW_ID_TITLE)
      titleView.setTextSize(dimensions.overviewContentTextSize)
      linesLayout.addView(titleView)
      linesLayout
    }

    override def bindView(view: View, context: Context, cursor: Cursor): Unit = {
      val title = cursor.getString(1)
      view.findViewById(VIEW_ID_TITLE).asInstanceOf[TextView].setText(title)
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val listView = new ListView(getActivity)
    listView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    listView.setId(ids.nextId)
    listView
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setListAdapter(adapter)
    getLoaderManager.initLoader(0, null, this)
  }

  override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon
    ContentUris.appendId(builder, System.currentTimeMillis)
    ContentUris.appendId(builder, System.currentTimeMillis() + 20 * 24 * 60 * 60 * 1000)

    new CursorLoader(activity, builder.build, Array("_id", "title"), "", null, null)
  }

  override def onLoadFinished(loader: Loader[Cursor], data: Cursor): Unit = adapter.swapCursor(data)

  override def onLoaderReset(loader: Loader[Cursor]): Unit = adapter.swapCursor(null)

  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    Log.d(TAG, getClass.getSimpleName + " got a click with position == " + position + " , id == " + id)
  }

}
