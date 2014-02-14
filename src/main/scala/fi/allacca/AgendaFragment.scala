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
import org.joda.time.LocalDate

class AgendaFragment extends ListFragment with LoaderManager.LoaderCallbacks[Cursor] {
  private val ids = new IdGenerator
  private lazy val activity = getActivity
  private lazy val dimensions = new ScreenParameters(getResources.getDisplayMetrics)
  private var displayRange: (LocalDate, LocalDate) = (new LocalDate(), new LocalDate().plusDays(20))

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

    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      if (position == 0) {
        displayRange = (displayRange._1.minusDays(10), displayRange._2.minusDays(5))
        getLoaderManager.restartLoader(0, null, AgendaFragment.this)
        notifyDataSetChanged()
      }
      if (position >= getCount) {
        displayRange = (displayRange._1.plusDays(5), displayRange._2.plusDays(10))
        getLoaderManager.restartLoader(0, null, AgendaFragment.this)
        notifyDataSetChanged()

      }
      super.getView(position, convertView, parent)
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
    ContentUris.appendId(builder, displayRange._1)
    ContentUris.appendId(builder, displayRange._2)
    Log.d(TAG, "Gonna load " + displayRange)
    new CursorLoader(activity, builder.build, Array("_id", "title"), "", null, null)
  }

  override def onLoadFinished(loader: Loader[Cursor], data: Cursor): Unit = adapter.swapCursor(data)

  override def onLoaderReset(loader: Loader[Cursor]): Unit = adapter.swapCursor(null)

  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    Log.d(TAG, getClass.getSimpleName + " got a click with position == " + position + " , id == " + id)
  }

}
