package com.example.myapp.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapp.R
import com.example.myapp.model.RoutineEntity
import com.example.myapp.model.RoutineRecordEntity
import com.example.myapp.model.Weekday
import com.google.android.material.card.MaterialCardView
import java.time.LocalDate

class RoutineAdapter(
    private var routines: List<RoutineEntity>,
    private val onItemClick: (routine: RoutineEntity) -> Unit,
    private val onAddClick: () -> Unit,
    private val onStartClick: (routine: RoutineEntity) -> Unit,
    private val onEditClick: (routine: RoutineEntity) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_ADD_BUTTON = 1
    }
    private var completedRoutineIds: Set<Int> = emptySet()
    override fun getItemCount(): Int = routines.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position == routines.size) TYPE_ADD_BUTTON else TYPE_ITEM
    }

    // 뷰 홀더 생성
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.routine_item, parent, false)
            RoutineViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_add_button, parent, false)
            AddButtonViewHolder(view)
        }
    }
    // 뷰 홀더에 데이터 바인딩
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is RoutineViewHolder && position < routines.size) {
            val routine = routines[position]
            holder.titleText.text = routine.title
            holder.itemView.setOnClickListener {
                onItemClick(routine)
            }
            // 스타트 버튼 클릭 리스너 설정
            val startButton = holder.itemView.findViewById<Button>(R.id.btn_start_routine)
            startButton.setOnClickListener {
                onStartClick(routine)
            }
            // 수정 버튼 클릭 리스너 설정
            val editButton = holder.itemView.findViewById<ImageButton>(R.id.btn_edit_routine)
            editButton.setOnClickListener {
                onEditClick(routine)
            }
            val today = LocalDate.now().dayOfWeek
            val todayWeekday = Weekday.valueOf(today.name)

            val isToday = routine.repeatOn.contains(todayWeekday)
            val isCompleted = completedRoutineIds.contains(routine.id)
            val cardView = holder.itemView as? MaterialCardView
            val statusIcon = holder.todayIndicator

            if (isCompleted) {
                // 1. 완료되었다면 체크 아이콘 표시
                statusIcon.visibility = View.VISIBLE
                statusIcon.setImageResource(R.drawable.ic_check_circle)
            } else if (isToday) {
                // 2. 오늘 할 일이지만 아직 미완료 상태면 별 아이콘 표시
                statusIcon.visibility = View.VISIBLE
                statusIcon.setImageResource(R.drawable.ic_today_star)
            } else {
                // 3. 그 외의 경우에는 아이콘 숨김
                statusIcon.visibility = View.GONE
            }
//            if (isToday && !isCompleted) {
//                cardView?.strokeWidth = 3 // 테두리 두께 (pixel 단위)
//                cardView?.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.primary_blue) // 테두리 색상
//            } else {
//                cardView?.strokeWidth = 0
//            }

        } else if (holder is AddButtonViewHolder) {
            holder.addButton.setOnClickListener {
                onAddClick()
            }
        }
    }

    class RoutineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.tv_routine_title)
        val todayIndicator: ImageView = itemView.findViewById(R.id.iv_today_indicator)
    }

    class AddButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val addButton: Button = itemView.findViewById(R.id.btnAddRoutine)
    }

    fun submitList(newRoutines: List<RoutineEntity>) {
        routines = newRoutines
        notifyDataSetChanged()
    }
    fun updateCompletedRoutines(records: List<RoutineRecordEntity>) {
        completedRoutineIds = records.map { it.routineId }.toSet()
        notifyDataSetChanged()
    }
}