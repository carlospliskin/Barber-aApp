import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/app_button.dart';
import '../../../../shared/widgets/app_components.dart';
import '../../domain/entities/client_entities.dart';
import '../notifiers/home_client_notifier.dart';
import 'client_widgets.dart';

void showBookingSheet(BuildContext context) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (_) => ProviderScope(
      parent: ProviderScope.containerOf(context),
      child: const _BookingSheet(),
    ),
  );
}

class _BookingSheet extends ConsumerStatefulWidget {
  const _BookingSheet();

  @override
  ConsumerState<_BookingSheet> createState() => _BookingSheetState();
}

class _BookingSheetState extends ConsumerState<_BookingSheet> {
  int _step = 0; // 0=Servicio, 1=Barbero+Fecha, 2=Slot, 3=Confirmar

  @override
  Widget build(BuildContext context) {
    final state    = ref.watch(homeClientNotifierProvider);
    final barberia = state.selectedBarberia;
    if (barberia == null) return const SizedBox.shrink();

    final bottomPadding = MediaQuery.of(context).viewInsets.bottom +
        MediaQuery.of(context).padding.bottom;

    return Container(
      height: MediaQuery.of(context).size.height * 0.88,
      decoration: const BoxDecoration(
        color: AppColors.graphite,
        borderRadius: AppRadius.rBottomSheet,
      ),
      padding: EdgeInsets.only(bottom: bottomPadding),
      child: Column(
        children: [
          // Handle + header
          _buildHeader(context, barberia),
          // Indicador de pasos
          _StepIndicator(currentStep: _step, totalSteps: 4),
          // Contenido del paso
          Expanded(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 280),
              switchInCurve: Curves.easeOutCubic,
              switchOutCurve: Curves.easeInCubic,
              transitionBuilder: (child, anim) => FadeTransition(
                opacity: anim,
                child: SlideTransition(
                  position: Tween<Offset>(
                    begin: const Offset(0.05, 0),
                    end: Offset.zero,
                  ).animate(anim),
                  child: child,
                ),
              ),
              child: _buildStep(state, barberia),
            ),
          ),
          // Footer con botón
          _buildFooter(state),
        ],
      ),
    ).animate().slideY(begin: 0.3, duration: 350.ms, curve: Curves.easeOutCubic);
  }

  Widget _buildHeader(BuildContext context, BarberiaInfo barberia) {
    return Column(
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
              if (_step > 0)
                GestureDetector(
                  onTap: () => setState(() => _step--),
                  child: const Icon(Icons.arrow_back_ios_rounded,
                    size: 16, color: AppColors.silver),
                )
              else
                const SizedBox(width: 16),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(_stepTitle, style: AppTypography.headingMd),
                    Text(barberia.name,
                      style: AppTypography.monoSm.copyWith(color: AppColors.gold)),
                  ],
                ),
              ),
              GestureDetector(
                onTap: () {
                  ref.read(homeClientNotifierProvider.notifier)
                      .clearBookingSelection();
                  Navigator.pop(context);
                },
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
      ],
    );
  }

  Widget _buildStep(HomeClientState state, BarberiaInfo barberia) {
    return switch (_step) {
      0 => _StepService(
          key: const ValueKey(0),
          services: barberia.services,
          selected: state.selectedService,
          onSelect: (svc) {
            ref.read(homeClientNotifierProvider.notifier).selectService(svc);
          },
        ),
      1 => _StepBarber(
          key: const ValueKey(1),
          barbers: barberia.barbers,
          selected: state.selectedBarber,
          selectedDate: state.selectedDate,
          onSelectBarber: (b) =>
              ref.read(homeClientNotifierProvider.notifier).selectBarber(b),
          onSelectDate: (d) =>
              ref.read(homeClientNotifierProvider.notifier).selectDate(d),
        ),
      2 => _StepSlot(
          key: const ValueKey(2),
          slots: state.slotsForSelectedBarber,
          selected: state.selectedSlot,
          onSelect: (slot) =>
              ref.read(homeClientNotifierProvider.notifier).selectSlot(slot),
        ),
      3 => _StepConfirm(
          key: const ValueKey(3),
          state: state,
          barberia: barberia,
        ),
      _ => const SizedBox.shrink(),
    };
  }

  Widget _buildFooter(HomeClientState state) {
    final canNext = switch (_step) {
      0 => state.selectedService != null,
      1 => state.selectedBarber != null && state.selectedDate != null,
      2 => state.selectedSlot != null,
      3 => true,
      _ => false,
    };

    return Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: AppButton(
        label: _step < 3 ? 'Continuar' : 'Confirmar reserva',
        loading: state.isBooking,
        onTap: canNext && !state.isBooking
            ? () => _handleNext(state)
            : null,
        trailing: _step < 3
            ? const Icon(Icons.arrow_forward_rounded,
                size: 16, color: AppColors.obsidian)
            : null,
      ),
    );
  }

  Future<void> _handleNext(HomeClientState state) async {
    HapticFeedback.lightImpact();
    if (_step < 3) {
      setState(() => _step++);
      return;
    }
    // Paso 3 → confirmar
    final result = await ref
        .read(homeClientNotifierProvider.notifier)
        .confirmBooking();

    if (!mounted) return;

    if (result != null) {
      Navigator.pop(context);
      _showBookingSuccess(context, result);
    } else {
      final failure = ref.read(homeClientNotifierProvider).failure;
      if (failure != null) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(failure.userMessage,
            style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
          backgroundColor: AppColors.error,
          behavior: SnackBarBehavior.floating,
        ));
      }
    }
  }

  void _showBookingSuccess(BuildContext context, BookingResult result) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Row(
          children: [
            const Icon(Icons.check_circle_rounded,
              color: AppColors.confirmed, size: 24),
            const SizedBox(width: AppSpacing.sm),
            Text('¡Cita reservada!', style: AppTypography.headingMd),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Tu código de confirmación:',
              style: AppTypography.bodySm),
            const SizedBox(height: AppSpacing.sm),
            Container(
              padding: const EdgeInsets.all(AppSpacing.md),
              decoration: BoxDecoration(
                color: AppColors.slate,
                borderRadius: AppRadius.rSm,
              ),
              child: Text(
                result.confirmationCode,
                style: AppTypography.monoLg.copyWith(fontSize: 22),
                textAlign: TextAlign.center,
              ),
            ),
            if (result.requiresPayment) ...[
              const SizedBox(height: AppSpacing.md),
              GoldGlowContainer(
                child: Text(
                  'Se requiere un depósito para confirmar tu cita.',
                  style: AppTypography.bodyMd.copyWith(color: AppColors.pearl),
                ),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('Listo',
              style: AppTypography.labelLg.copyWith(color: AppColors.gold)),
          ),
        ],
      ),
    );
  }

  String get _stepTitle => switch (_step) {
    0 => 'Elige tu servicio',
    1 => 'Elige barbero y fecha',
    2 => 'Elige tu horario',
    3 => 'Confirmar reserva',
    _ => '',
  };
}

// ─── Pasos del booking ────────────────────────────────────────────────────────

class _StepService extends StatelessWidget {
  final List<PublicService> services;
  final PublicService? selected;
  final ValueChanged<PublicService> onSelect;

  const _StepService({
    super.key,
    required this.services,
    required this.selected,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    // Agrupar por categoría
    final byCategory = <String, List<PublicService>>{};
    for (final s in services) {
      byCategory.putIfAbsent(s.category, () => []).add(s);
    }

    return ListView(
      padding: const EdgeInsets.all(AppSpacing.base),
      children: byCategory.entries.map((entry) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.sm),
            child: Text(entry.key,
              style: AppTypography.overline.copyWith(color: AppColors.ash)),
          ),
          ...entry.value.map((svc) => _ServiceTile(
            service: svc,
            isSelected: selected?.id == svc.id,
            onTap: () => onSelect(svc),
          )),
          const SizedBox(height: AppSpacing.md),
        ],
      )).toList(),
    );
  }
}

class _ServiceTile extends StatelessWidget {
  final PublicService service;
  final bool isSelected;
  final VoidCallback onTap;

  const _ServiceTile({
    required this.service,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        onTap();
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.gold.withOpacity(0.08) : AppColors.slate,
          borderRadius: AppRadius.rCard,
          border: Border.all(
            color: isSelected
                ? AppColors.gold.withOpacity(0.5)
                : AppColors.smoke),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(service.name, style: AppTypography.headingSm.copyWith(
                    color: isSelected ? AppColors.white : AppColors.pearl)),
                  const SizedBox(height: 2),
                  Row(
                    children: [
                      Text(service.priceLabel,
                        style: AppTypography.monoMd.copyWith(
                          color: isSelected ? AppColors.gold : AppColors.silver,
                          fontSize: 14)),
                      const SizedBox(width: AppSpacing.sm),
                      Text('·  ${service.durationLabel}',
                        style: AppTypography.bodySm),
                      if (service.requiresDeposit) ...[
                        const SizedBox(width: AppSpacing.sm),
                        Text('· Depósito \$${service.depositAmount}',
                          style: AppTypography.bodySm.copyWith(
                            color: AppColors.gold, fontSize: 10)),
                      ],
                    ],
                  ),
                ],
              ),
            ),
            if (isSelected)
              const Icon(Icons.check_circle_rounded,
                color: AppColors.gold, size: 20),
          ],
        ),
      ),
    );
  }
}

class _StepBarber extends StatelessWidget {
  final List<PublicBarber> barbers;
  final PublicBarber? selected;
  final DateTime? selectedDate;
  final ValueChanged<PublicBarber> onSelectBarber;
  final ValueChanged<DateTime> onSelectDate;

  const _StepBarber({
    super.key,
    required this.barbers,
    required this.selected,
    required this.selectedDate,
    required this.onSelectBarber,
    required this.onSelectDate,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.base),
      children: [
        // Selector de fecha
        Text('FECHA', style: AppTypography.overline.copyWith(
          color: AppColors.ash)),
        const SizedBox(height: AppSpacing.sm),
        _DatePickerRow(
          selectedDate: selectedDate,
          onSelect: onSelectDate,
        ),
        const SizedBox(height: AppSpacing.xl),
        // Barberos
        Text('BARBERO', style: AppTypography.overline.copyWith(
          color: AppColors.ash)),
        const SizedBox(height: AppSpacing.sm),
        ...barbers.map((b) => _BarberTile(
          barber: b,
          isSelected: selected?.id == b.id,
          onTap: () => onSelectBarber(b),
        )),
      ],
    );
  }
}

class _DatePickerRow extends StatelessWidget {
  final DateTime? selectedDate;
  final ValueChanged<DateTime> onSelect;

  const _DatePickerRow({required this.selectedDate, required this.onSelect});

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final dates = List.generate(14, (i) => now.add(Duration(days: i)));
    const dayNames = ['LUN','MAR','MIÉ','JUE','VIE','SÁB','DOM'];

    return SizedBox(
      height: 68,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: dates.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppSpacing.sm),
        itemBuilder: (_, i) {
          final date = dates[i];
          final isSelected = selectedDate != null &&
              _sameDay(date, selectedDate!);
          final dayIdx = date.weekday - 1;

          return GestureDetector(
            onTap: () {
              HapticFeedback.selectionClick();
              onSelect(date);
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: 44,
              decoration: BoxDecoration(
                gradient: isSelected ? AppColors.goldGradient : null,
                color: isSelected ? null : AppColors.graphite,
                borderRadius: AppRadius.rMd,
                border: Border.all(
                  color: isSelected ? Colors.transparent : AppColors.smoke),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(dayNames[dayIdx],
                    style: AppTypography.overline.copyWith(
                      color: isSelected ? AppColors.obsidian : AppColors.ash,
                      fontSize: 8)),
                  const SizedBox(height: 4),
                  Text('${date.day}',
                    style: AppTypography.monoMd.copyWith(
                      color: isSelected ? AppColors.obsidian : AppColors.white,
                      fontSize: 16)),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  bool _sameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;
}

class _BarberTile extends StatelessWidget {
  final PublicBarber barber;
  final bool isSelected;
  final VoidCallback onTap;

  const _BarberTile({
    required this.barber,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final hasSlots = barber.availableSlots.isNotEmpty;
    return GestureDetector(
      onTap: hasSlots ? () {
        HapticFeedback.selectionClick();
        onTap();
      } : null,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: isSelected
              ? AppColors.gold.withOpacity(0.08)
              : hasSlots ? AppColors.slate : AppColors.graphite,
          borderRadius: AppRadius.rCard,
          border: Border.all(
            color: isSelected
                ? AppColors.gold.withOpacity(0.5)
                : AppColors.smoke),
        ),
        child: Row(
          children: [
            Container(
              width: 36, height: 36,
              decoration: BoxDecoration(
                color: isSelected ? AppColors.goldSubtle : AppColors.slate,
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Text(barber.name[0].toUpperCase(),
                  style: AppTypography.headingSm.copyWith(
                    color: isSelected ? AppColors.gold : AppColors.ash)),
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(barber.name, style: AppTypography.labelLg.copyWith(
                    color: hasSlots ? AppColors.white : AppColors.ash)),
                  Row(
                    children: [
                      if (barber.avgRating > 0) ...[
                        const Icon(Icons.star_rounded,
                          size: 11, color: AppColors.gold),
                        const SizedBox(width: 2),
                        Text(barber.avgRating.toStringAsFixed(1),
                          style: AppTypography.monoSm.copyWith(
                            fontSize: 10, color: AppColors.gold)),
                        const SizedBox(width: AppSpacing.sm),
                      ],
                      Text(
                        hasSlots
                            ? '${barber.availableSlots.length} horarios'
                            : 'Sin disponibilidad',
                        style: AppTypography.bodySm.copyWith(
                          color: hasSlots
                              ? AppColors.confirmed : AppColors.ash,
                          fontSize: 11),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            if (isSelected)
              const Icon(Icons.check_circle_rounded,
                color: AppColors.gold, size: 20),
          ],
        ),
      ),
    );
  }
}

class _StepSlot extends StatelessWidget {
  final List<AvailableSlot> slots;
  final AvailableSlot? selected;
  final ValueChanged<AvailableSlot> onSelect;

  const _StepSlot({
    super.key,
    required this.slots,
    required this.selected,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('HORARIOS DISPONIBLES',
            style: AppTypography.overline.copyWith(color: AppColors.ash)),
          const SizedBox(height: AppSpacing.md),
          SlotPicker(
            slots: slots,
            selected: selected,
            onSelected: onSelect,
          ),
        ],
      ),
    );
  }
}

class _StepConfirm extends StatelessWidget {
  final HomeClientState state;
  final BarberiaInfo barberia;

  const _StepConfirm({
    super.key,
    required this.state,
    required this.barberia,
  });

  @override
  Widget build(BuildContext context) {
    final svc     = state.selectedService!;
    final barber  = state.selectedBarber!;
    final date    = state.selectedDate!;
    final slot    = state.selectedSlot!;
    final endTime = slot.toDateTime(date).add(
      Duration(minutes: svc.durationMinutes));
    final endH = endTime.hour.toString().padLeft(2, '0');
    final endM = endTime.minute.toString().padLeft(2, '0');

    return SingleChildScrollView(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          GoldGlowContainer(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('RESUMEN DE TU CITA',
                  style: AppTypography.overline.copyWith(color: AppColors.ash)),
                const SizedBox(height: AppSpacing.md),
                _Row(Icons.storefront_outlined,  barberia.name),
                _Row(Icons.content_cut_rounded,  svc.name),
                _Row(Icons.person_rounded,        barber.name),
                _Row(Icons.calendar_today_rounded,
                  '${date.day}/${date.month}/${date.year}'),
                _Row(Icons.schedule_rounded,
                  '${slot.time} – $endH:$endM'),
                _Row(Icons.attach_money_rounded,  svc.priceLabel),
                if (svc.requiresDeposit)
                  _Row(Icons.payment_rounded,
                    'Depósito: \$${svc.depositAmount} MXN',
                    color: AppColors.gold),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          if (svc.requiresDeposit) ...[
            Container(
              padding: const EdgeInsets.all(AppSpacing.md),
              decoration: BoxDecoration(
                color: AppColors.goldSubtle,
                borderRadius: AppRadius.rCard,
                border: Border.all(color: AppColors.gold.withOpacity(0.3)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.info_outline_rounded,
                    size: 16, color: AppColors.gold),
                  const SizedBox(width: AppSpacing.sm),
                  Expanded(
                    child: Text(
                      'Se requerirá un depósito de \$${svc.depositAmount} MXN '
                      'para confirmar tu cita.',
                      style: AppTypography.bodyMd.copyWith(
                        color: AppColors.pearl, fontSize: 12),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _Row extends StatelessWidget {
  final IconData icon;
  final String text;
  final Color? color;

  const _Row(this.icon, this.text, {this.color});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Row(
        children: [
          Icon(icon, size: 14, color: color ?? AppColors.ash),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Text(text, style: AppTypography.bodyMd.copyWith(
            color: color ?? AppColors.pearl))),
        ],
      ),
    );
  }
}

class _StepIndicator extends StatelessWidget {
  final int currentStep;
  final int totalSteps;

  const _StepIndicator({required this.currentStep, required this.totalSteps});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.base, vertical: AppSpacing.sm),
      child: Row(
        children: List.generate(totalSteps, (i) => Expanded(
          child: Container(
            margin: EdgeInsets.only(right: i < totalSteps - 1 ? 4 : 0),
            height: 3,
            decoration: BoxDecoration(
              color: i <= currentStep ? AppColors.gold : AppColors.smoke,
              borderRadius: AppRadius.rPill,
            ),
          ),
        )),
      ),
    );
  }
}
