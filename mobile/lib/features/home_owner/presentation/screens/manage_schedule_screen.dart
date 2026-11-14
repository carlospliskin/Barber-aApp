import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../domain/entities/dashboard_entities.dart';
import '../notifiers/home_owner_notifier.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../notifiers/home_owner_notifier.dart';

class ManageScheduleScreen extends ConsumerWidget {
  const ManageScheduleScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final barbers = ref.watch(homeOwnerNotifierProvider.select((s) => s.barbers));

    return Scaffold(
      backgroundColor: AppColors.obsidian,
      appBar: AppBar(
        backgroundColor: AppColors.obsidian,
        title: Text('Horarios', style: AppTypography.headingMd),
        leading: GestureDetector(
          onTap: () => Navigator.pop(context),
          child: const Icon(Icons.arrow_back_ios_rounded,
            size: 18, color: AppColors.white),
        ),
      ),
      body: barbers.isEmpty
          ? _EmptyBarbers()
          : ListView.separated(
              padding: AppSpacing.screen,
              itemCount: barbers.length,
              separatorBuilder: (_, __) =>
                  const SizedBox(height: AppSpacing.md),
              itemBuilder: (_, i) => _BarberScheduleCard(
                barber: barbers[i],
                onEditSchedule: () => _showScheduleEditor(
                  context, ref, barbers[i]),
              ).animate().fadeIn(delay: Duration(milliseconds: 80 * i)),
            ),
    );
  }

  void _showScheduleEditor(
    BuildContext context,
    WidgetRef ref,
    BarberWithSchedule barber,
  ) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => ProviderScope(
        parent: ProviderScope.containerOf(context),
        child: _ScheduleEditorSheet(barber: barber),
      ),
    );
  }
}

// ─── _BarberScheduleCard ──────────────────────────────────────────────────────

class _BarberScheduleCard extends StatelessWidget {
  final BarberWithSchedule barber;
  final VoidCallback onEditSchedule;

  const _BarberScheduleCard({
    required this.barber,
    required this.onEditSchedule,
  });

  static const _dayNames = ['LUN','MAR','MIÉ','JUE','VIE','SÁB','DOM'];

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header barbero
          Row(
            children: [
              Container(
                width: 40, height: 40,
                decoration: BoxDecoration(
                  gradient: AppColors.goldGradient,
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Text(
                    barber.name.isNotEmpty ? barber.name[0].toUpperCase() : 'B',
                    style: AppTypography.headingSm.copyWith(
                      color: AppColors.obsidian,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(barber.name, style: AppTypography.headingSm),
                    Text(
                      barber.scheduleLabel,
                      style: AppTypography.monoSm.copyWith(
                        color: barber.isWorkingToday
                            ? AppColors.gold : AppColors.ash,
                      ),
                    ),
                  ],
                ),
              ),
              GestureDetector(
                onTap: onEditSchedule,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.md, vertical: AppSpacing.xs + 2),
                  decoration: BoxDecoration(
                    color: AppColors.slate,
                    borderRadius: AppRadius.rPill,
                    border: Border.all(color: AppColors.smoke),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.edit_rounded,
                        size: 12, color: AppColors.silver),
                      const SizedBox(width: 4),
                      Text('Editar', style: AppTypography.labelSm.copyWith(
                        color: AppColors.silver)),
                    ],
                  ),
                ),
              ),
            ],
          ),

          const SizedBox(height: AppSpacing.md),
          Container(height: 1, color: AppColors.smoke),
          const SizedBox(height: AppSpacing.md),

          // Vista semanal rápida — 7 puntos
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: List.generate(7, (i) {
              // Por ahora mostramos el día actual como activo
              final isActive = i == DateTime.now().weekday - 1 &&
                  barber.isWorkingToday;
              return Column(
                children: [
                  Container(
                    width: 32, height: 32,
                    decoration: BoxDecoration(
                      color: isActive
                          ? AppColors.goldSubtle
                          : AppColors.slate,
                      borderRadius: AppRadius.rSm,
                      border: Border.all(
                        color: isActive
                            ? AppColors.gold.withOpacity(0.4)
                            : AppColors.smoke),
                    ),
                    child: Center(
                      child: Text(
                        _dayNames[i],
                        style: AppTypography.overline.copyWith(
                          color: isActive ? AppColors.gold : AppColors.ash,
                          fontSize: 8,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 4),
                  Container(
                    width: 5, height: 5,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: isActive ? AppColors.gold : AppColors.smoke,
                    ),
                  ),
                ],
              );
            }),
          ),
        ],
      ),
    );
  }
}

// ─── _ScheduleEditorSheet ─────────────────────────────────────────────────────

class _ScheduleEditorSheet extends ConsumerStatefulWidget {
  final BarberWithSchedule barber;
  const _ScheduleEditorSheet({required this.barber});

  @override
  ConsumerState<_ScheduleEditorSheet> createState() =>
      _ScheduleEditorSheetState();
}

class _ScheduleEditorSheetState extends ConsumerState<_ScheduleEditorSheet> {
  static const _days = [
    'Lunes','Martes','Miércoles','Jueves',
    'Viernes','Sábado','Domingo',
  ];

  // Estado local del horario (índice = día 0-6)
  late List<_DaySchedule> _schedule;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    // Inicializar con el horario actual (simplificado — solo hoy viene del backend)
    _schedule = List.generate(7, (i) {
      final isToday = i == DateTime.now().weekday - 1;
      return _DaySchedule(
        dayIndex: i,
        isWorking: isToday && widget.barber.isWorkingToday,
        openTime: isToday ? widget.barber.openTime ?? '10:00' : '10:00',
        closeTime: isToday ? widget.barber.closeTime ?? '20:00' : '20:00',
        capacity: widget.barber.capacity,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    final bottomPadding = MediaQuery.of(context).viewInsets.bottom +
        MediaQuery.of(context).padding.bottom;

    return Container(
      height: MediaQuery.of(context).size.height * 0.85,
      decoration: const BoxDecoration(
        color: AppColors.graphite, borderRadius: AppRadius.rBottomSheet),
      padding: EdgeInsets.only(bottom: bottomPadding),
      child: Column(
        children: [
          Container(
            margin: const EdgeInsets.only(top: AppSpacing.md),
            width: 40, height: 4,
            decoration: BoxDecoration(
              color: AppColors.smoke, borderRadius: AppRadius.rPill),
          ),
          Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Horario semanal',
                        style: AppTypography.headingMd),
                      Text(widget.barber.name,
                        style: AppTypography.monoSm.copyWith(
                          color: AppColors.gold)),
                    ],
                  ),
                ),
                GestureDetector(
                  onTap: () => Navigator.pop(context),
                  child: Container(
                    width: 32, height: 32,
                    decoration: BoxDecoration(
                      color: AppColors.slate, borderRadius: AppRadius.rSm),
                    child: const Icon(Icons.close_rounded,
                      size: 16, color: AppColors.silver),
                  ),
                ),
              ],
            ),
          ),
          Container(height: 1, color: AppColors.smoke),
          Expanded(
            child: ListView.separated(
              padding: const EdgeInsets.all(AppSpacing.base),
              itemCount: 7,
              separatorBuilder: (_, __) =>
                  const SizedBox(height: AppSpacing.md),
              itemBuilder: (_, i) => _DayRow(
                day: _schedule[i],
                label: _days[i],
                onToggle: (val) => setState(
                    () => _schedule[i] = _schedule[i].copyWith(isWorking: val)),
                onOpenTime: () => _pickTime(i, isOpen: true),
                onCloseTime: () => _pickTime(i, isOpen: false),
                onCapacity: (val) => setState(
                    () => _schedule[i] = _schedule[i].copyWith(capacity: val)),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: AppButton(
              label: 'Guardar horario',
              loading: _isLoading,
              onTap: _save,
            ),
          ),
        ],
      ),
    ).animate().slideY(begin: 0.3, duration: 350.ms, curve: Curves.easeOutCubic);
  }

  Future<void> _pickTime(int dayIndex, {required bool isOpen}) async {
    final current = _schedule[dayIndex];
    final parts = (isOpen ? current.openTime : current.closeTime).split(':');
    final initial = TimeOfDay(
      hour: int.tryParse(parts[0]) ?? 10,
      minute: int.tryParse(parts[1]) ?? 0,
    );

    final picked = await showTimePicker(
      context: context,
      initialTime: initial,
      builder: (ctx, child) => Theme(
        data: ThemeData.dark().copyWith(colorScheme: const ColorScheme.dark(
          primary: AppColors.gold, onPrimary: AppColors.obsidian,
          surface: AppColors.graphite, onSurface: AppColors.white,
        )),
        child: child!,
      ),
    );

    if (picked == null || !mounted) return;
    final timeStr =
        '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';

    setState(() {
      _schedule[dayIndex] = isOpen
          ? current.copyWith(openTime: timeStr)
          : current.copyWith(closeTime: timeStr);
    });
  }

  Future<void> _save() async {
    setState(() => _isLoading = true);
    HapticFeedback.mediumImpact();

    final scheduleInputs = _schedule.map((s) => DayScheduleInput(
      dayOfWeek: s.dayIndex,
      isWorking: s.isWorking,
      openTime:  s.openTime,
      closeTime: s.closeTime,
      capacity:  s.capacity,
    )).toList();

    final result = await ref
        .read(homeOwnerNotifierProvider.notifier)
        .saveBarberSchedule(
          barberId:  widget.barber.memberId,
          schedules: scheduleInputs,
        );

    if (!mounted) return;
    setState(() => _isLoading = false);

    result.fold(
      (failure) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(failure.userMessage,
          style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
        backgroundColor: AppColors.error,
        behavior: SnackBarBehavior.floating,
      )),
      (_) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Horario guardado',
            style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
          backgroundColor: AppColors.confirmed,
          behavior: SnackBarBehavior.floating,
        ));
      },
    );
  }
}

// ─── _DayRow ──────────────────────────────────────────────────────────────────

class _DayRow extends StatelessWidget {
  final _DaySchedule day;
  final String label;
  final ValueChanged<bool> onToggle;
  final VoidCallback onOpenTime;
  final VoidCallback onCloseTime;
  final ValueChanged<int> onCapacity;

  const _DayRow({
    required this.day,
    required this.label,
    required this.onToggle,
    required this.onOpenTime,
    required this.onCloseTime,
    required this.onCapacity,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: day.isWorking ? AppColors.slate : AppColors.graphite,
        borderRadius: AppRadius.rCard,
        border: Border.all(
          color: day.isWorking
              ? AppColors.gold.withOpacity(0.25)
              : AppColors.smoke),
      ),
      child: Column(
        children: [
          Row(
            children: [
              // Día
              SizedBox(
                width: 60,
                child: Text(label, style: AppTypography.labelLg.copyWith(
                  color: day.isWorking ? AppColors.white : AppColors.ash)),
              ),
              const Spacer(),
              // Toggle trabajando
              GestureDetector(
                onTap: () {
                  HapticFeedback.selectionClick();
                  onToggle(!day.isWorking);
                },
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  width: 44, height: 24,
                  decoration: BoxDecoration(
                    color: day.isWorking ? AppColors.gold : AppColors.smoke,
                    borderRadius: AppRadius.rPill,
                  ),
                  child: AnimatedAlign(
                    duration: const Duration(milliseconds: 200),
                    alignment: day.isWorking
                        ? Alignment.centerRight : Alignment.centerLeft,
                    child: Container(
                      width: 20, height: 20,
                      margin: const EdgeInsets.all(2),
                      decoration: BoxDecoration(
                        color: day.isWorking
                            ? AppColors.obsidian : AppColors.graphite,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
          if (day.isWorking) ...[
            const SizedBox(height: AppSpacing.md),
            Row(
              children: [
                // Hora entrada
                Expanded(
                  child: _TimeButton(
                    label: 'Entrada',
                    time: day.openTime,
                    onTap: onOpenTime,
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
                  child: Text('–', style: AppTypography.bodyMd.copyWith(
                    color: AppColors.ash)),
                ),
                // Hora salida
                Expanded(
                  child: _TimeButton(
                    label: 'Salida',
                    time: day.closeTime,
                    onTap: onCloseTime,
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                // Capacity
                _CapacitySelector(
                  value: day.capacity,
                  onChanged: onCapacity,
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _TimeButton extends StatelessWidget {
  final String label;
  final String time;
  final VoidCallback onTap;

  const _TimeButton({
    required this.label,
    required this.time,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md, vertical: AppSpacing.sm),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rSm,
          border: Border.all(color: AppColors.smoke),
        ),
        child: Column(
          children: [
            Text(label, style: AppTypography.overline.copyWith(
              color: AppColors.ash, fontSize: 9)),
            const SizedBox(height: 2),
            Text(time, style: AppTypography.monoMd.copyWith(fontSize: 14)),
          ],
        ),
      ),
    );
  }
}

class _CapacitySelector extends StatelessWidget {
  final int value;
  final ValueChanged<int> onChanged;

  const _CapacitySelector({required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text('Simult.', style: AppTypography.overline.copyWith(
          color: AppColors.ash, fontSize: 9)),
        const SizedBox(height: 4),
        Row(
          children: [1, 2].map((cap) => GestureDetector(
            onTap: () {
              HapticFeedback.selectionClick();
              onChanged(cap);
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: 28, height: 28,
              margin: EdgeInsets.only(left: cap == 2 ? 4 : 0),
              decoration: BoxDecoration(
                color: value == cap ? AppColors.gold : AppColors.slate,
                borderRadius: AppRadius.rSm,
                border: Border.all(
                  color: value == cap ? AppColors.gold : AppColors.smoke),
              ),
              child: Center(
                child: Text('$cap', style: AppTypography.monoSm.copyWith(
                  color: value == cap ? AppColors.obsidian : AppColors.ash,
                  fontSize: 12, fontWeight: FontWeight.w700,
                )),
              ),
            ),
          )).toList(),
        ),
      ],
    );
  }
}

// ─── Data classes locales ────────────────────────────────────────────────────

class _DaySchedule {
  final int dayIndex;
  final bool isWorking;
  final String openTime;
  final String closeTime;
  final int capacity;

  const _DaySchedule({
    required this.dayIndex,
    required this.isWorking,
    required this.openTime,
    required this.closeTime,
    required this.capacity,
  });

  _DaySchedule copyWith({
    bool? isWorking,
    String? openTime,
    String? closeTime,
    int? capacity,
  }) => _DaySchedule(
    dayIndex:  dayIndex,
    isWorking: isWorking  ?? this.isWorking,
    openTime:  openTime   ?? this.openTime,
    closeTime: closeTime  ?? this.closeTime,
    capacity:  capacity   ?? this.capacity,
  );
}

class _EmptyBarbers extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 72, height: 72,
            decoration: BoxDecoration(
              color: AppColors.graphite, shape: BoxShape.circle,
              border: Border.all(color: AppColors.smoke)),
            child: const Icon(Icons.schedule_rounded,
              size: 28, color: AppColors.ash),
          ),
          const SizedBox(height: AppSpacing.md),
          Text('Sin barberos registrados',
            style: AppTypography.headingSm.copyWith(color: AppColors.silver)),
          const SizedBox(height: AppSpacing.xs),
          Text('Agrega barberos desde la configuración',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash)),
        ],
      ),
    );
  }
}
