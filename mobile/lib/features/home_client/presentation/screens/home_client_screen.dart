import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_typography.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../features/auth/domain/entities/auth_user.dart';
import '../../../../features/auth/presentation/notifiers/auth_notifier.dart';
import '../../../../shared/widgets/app_components.dart';
import '../notifiers/home_client_notifier.dart';
import '../notifiers/home_client_state.dart';
import '../widgets/booking_sheet.dart';
import '../widgets/client_widgets.dart';

class HomeClientScreen extends ConsumerWidget {
  const HomeClientScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state    = ref.watch(homeClientNotifierProvider);
    final notifier = ref.read(homeClientNotifierProvider.notifier);
    final user     = ref.watch(currentUserProvider);

    ref.listen(
      homeClientNotifierProvider.select((s) => s.failure),
      (_, f) {
        if (f != null) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(f.userMessage,
              style: AppTypography.bodyMd.copyWith(color: AppColors.white)),
            backgroundColor: AppColors.error,
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: AppRadius.rMd),
          ));
          notifier.clearError();
        }
      },
    );

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(
        statusBarColor: Colors.transparent),
      child: Scaffold(
        backgroundColor: AppColors.obsidian,
        // IndexedStack mantiene el estado de cada tab al navegar
        body: IndexedStack(
          index: state.activeTab,
          children: [
            _SearchTab(ref: ref, user: user, state: state, notifier: notifier),
            _AppointmentsTab(state: state, notifier: notifier),
            _FavoritesTab(ref: ref, state: state, notifier: notifier),
          ],
        ),
        bottomNavigationBar: _BottomNav(
          activeTab: state.activeTab,
          onTab: notifier.setTab,
          // Mostrar botón "Negocio" si el cliente también es owner o barber
          onSwitchToOwner: (user?.isOwner == true || user?.isBarber == true)
              ? () => ref.read(authNotifierProvider.notifier)
                  .switchMode(user!.isOwner ? 'OWNER' : 'BARBER')
              : null,
        ),
      ),
    );
  }
}

// ─── Tab 0: Explorar ─────────────────────────────────────────────────────────

class _SearchTab extends StatelessWidget {
  final WidgetRef ref;
  final AuthUser? user;
  final HomeClientState state;
  final HomeClientNotifier notifier;

  const _SearchTab({
    required this.ref,
    required this.user,
    required this.state,
    required this.notifier,
  });

  @override
  Widget build(BuildContext context) {
    final firstName = user?.displayName.split(' ').first ?? '';

    return RefreshIndicator(
      color: AppColors.gold,
      backgroundColor: AppColors.graphite,
      onRefresh: notifier.refresh,
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [

          // ── Header ──────────────────────────────────────────────────────
          SliverToBoxAdapter(
            child: SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.base, vertical: AppSpacing.md),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(_greeting(), style: AppTypography.overline
                                  .copyWith(color: AppColors.ash)),
                              const SizedBox(height: 2),
                              Text(
                                firstName.isNotEmpty ? firstName : 'Bienvenido',
                                style: AppTypography.displayMd.copyWith(
                                  fontSize: 22)),
                            ],
                          ),
                        ),
                        // Avatar con logout
                        GestureDetector(
                          onTap: () => _showLogoutDialog(context),
                          child: Container(
                            width: 36, height: 36,
                            decoration: BoxDecoration(
                              gradient: AppColors.goldGradient,
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: AppColors.gold.withOpacity(0.4),
                                width: 1.5),
                            ),
                            child: Center(
                              child: Text(
                                firstName.isNotEmpty
                                    ? firstName[0].toUpperCase() : 'U',
                                style: AppTypography.labelLg.copyWith(
                                  color: AppColors.obsidian,
                                  fontWeight: FontWeight.w900),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    Text('¿Cuándo quieres ir?',
                      style: AppTypography.overline.copyWith(
                        color: AppColors.ash)),
                    const SizedBox(height: AppSpacing.sm),
                    _QuickDateSelector(onDate: notifier.searchForDate),
                  ],
                ).animate().fadeIn(duration: 400.ms),
              ),
            ),
          ),

          // ── Favoritas (acceso rápido horizontal) ─────────────────────────
          if (state.favorites.isNotEmpty)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.base),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(height: AppSpacing.lg),
                    Row(
                      children: [
                        Text('MIS FAVORITAS', style: AppTypography.overline
                            .copyWith(color: AppColors.ash)),
                        const SizedBox(width: AppSpacing.sm),
                        const Icon(Icons.favorite_rounded,
                          size: 12, color: AppColors.gold),
                      ],
                    ),
                    const SizedBox(height: AppSpacing.sm),
                    SizedBox(
                      height: 72,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: state.favorites.length,
                        separatorBuilder: (_, __) =>
                            const SizedBox(width: AppSpacing.sm),
                        itemBuilder: (ctx, i) => _FavChip(
                          barberia: state.favorites[i],
                          onTap: () => _openBarberia(
                            ctx, state.favorites[i].id),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

          // ── Header sección barberías ──────────────────────────────────────
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.base, AppSpacing.xl,
                AppSpacing.base, AppSpacing.sm),
              child: Row(
                children: [
                  Text('BARBERÍAS DISPONIBLES',
                    style: AppTypography.overline.copyWith(
                      color: AppColors.ash)),
                  const Spacer(),
                  if (state.barberíasLoading)
                    const SizedBox(
                      width: 12, height: 12,
                      child: CircularProgressIndicator(
                        strokeWidth: 1.5, color: AppColors.gold)),
                ],
              ),
            ),
          ),

          // ── Lista de barberías ────────────────────────────────────────────
          if (state.barberíasLoading && state.barberías.isEmpty)
            SliverList(
              delegate: SliverChildBuilderDelegate(
                (_, __) => const _BarberiaCardSkeleton(),
                childCount: 4,
              ),
            )
          else if (state.barberías.isEmpty)
            const SliverFillRemaining(
              child: _EmptyBarberías(),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.base),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (ctx, i) {
                    final b = state.barberías[i];
                    return BarberiaCard(
                      barberia: b,
                      onTap: () => _openBarberia(ctx, b.id),
                      onFavorite: () => notifier.toggleFavorite(b.id),
                    ).animate().fadeIn(
                      delay: Duration(milliseconds: 60 * i));
                  },
                  childCount: state.barberías.length,
                ),
              ),
            ),

          const SliverToBoxAdapter(child: SizedBox(height: 100)),
        ],
      ),
    );
  }

  // ─── Abrir barbería → seleccionar → mostrar booking ──────────────────────

  Future<void> _openBarberia(BuildContext context, String barberiaId) async {
    await notifier.selectBarberia(barberiaId);
    if (context.mounted) showBookingSheet(context);
  }

  // ─── Logout ──────────────────────────────────────────────────────────────

  Future<void> _showLogoutDialog(BuildContext context) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Cerrar sesión', style: AppTypography.headingMd),
        content: Text('¿Seguro que quieres salir?',
          style: AppTypography.bodyMd),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text('Cancelar', style: AppTypography.labelMd.copyWith(
              color: AppColors.silver)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Salir', style: AppTypography.labelMd.copyWith(
              color: AppColors.error, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (confirm == true && context.mounted) {
      ref.read(authNotifierProvider.notifier).logout();
    }
  }
}

// ─── Tab 1: Mis Citas ─────────────────────────────────────────────────────────

class _AppointmentsTab extends StatelessWidget {
  final HomeClientState state;
  final HomeClientNotifier notifier;

  const _AppointmentsTab({required this.state, required this.notifier});

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Text('Mis citas',
              style: AppTypography.displayMd.copyWith(fontSize: 22)),
          ),
          Expanded(
            child: state.appointmentsLoading
                ? const Center(child: CircularProgressIndicator(
                    color: AppColors.gold))
                : state.appointments.isEmpty
                    ? const _EmptyAppointments()
                    : DefaultTabController(
                        length: 2,
                        child: Column(
                          children: [
                            TabBar(
                              tabs: [
                                Tab(text: 'Próximas (${state.upcomingAppointments.length})'),
                                Tab(text: 'Historial'),
                              ],
                              labelColor: AppColors.gold,
                              unselectedLabelColor: AppColors.ash,
                              indicatorColor: AppColors.gold,
                              labelStyle: AppTypography.labelLg.copyWith(
                                fontSize: 12),
                            ),
                            Expanded(
                              child: TabBarView(
                                children: [
                                  _AppointmentList(
                                    appointments: state.upcomingAppointments,
                                    onCancel: (id) => _confirmCancel(
                                      context, id),
                                  ),
                                  _AppointmentList(
                                    appointments: state.pastAppointments,
                                    showCancelButton: false,
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmCancel(BuildContext context, String id) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.graphite,
        shape: RoundedRectangleBorder(borderRadius: AppRadius.rModal),
        title: Text('Cancelar cita', style: AppTypography.headingMd),
        content: Text(
          'Si cancelas, podría aplicarse una penalización '
          'dependiendo de las políticas de la barbería.',
          style: AppTypography.bodyMd,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: Text('Volver', style: AppTypography.labelMd.copyWith(
              color: AppColors.silver)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Cancelar cita',
              style: AppTypography.labelMd.copyWith(
                color: AppColors.error,
                fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (confirm == true) await notifier.cancelAppointment(id);
  }
}

// ─── Tab 2: Favoritas ─────────────────────────────────────────────────────────

class _FavoritesTab extends StatelessWidget {
  final WidgetRef ref;
  final HomeClientState state;
  final HomeClientNotifier notifier;

  const _FavoritesTab({
    required this.ref,
    required this.state,
    required this.notifier,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Text('Mis favoritas',
              style: AppTypography.displayMd.copyWith(fontSize: 22)),
          ),
          Expanded(
            child: state.favoritesLoading
                ? const Center(child: CircularProgressIndicator(
                    color: AppColors.gold))
                : state.favorites.isEmpty
                    ? const _EmptyFavorites()
                    : ListView.builder(
                        padding: AppSpacing.screen,
                        itemCount: state.favorites.length,
                        itemBuilder: (ctx, i) {
                          final fav = state.favorites[i];
                          return FavoriteBarberiaTile(
                            barberia: fav,
                            onTap: () async {
                              await notifier.selectBarberia(fav.id);
                              if (ctx.mounted) showBookingSheet(ctx);
                            },
                            onRemove: () => notifier.toggleFavorite(fav.id),
                          ).animate().fadeIn(
                            delay: Duration(milliseconds: 60 * i));
                        },
                      ),
          ),
        ],
      ),
    );
  }
}

// ─── BottomNav ────────────────────────────────────────────────────────────────

class _BottomNav extends StatelessWidget {
  final int activeTab;
  final ValueChanged<int> onTab;
  final VoidCallback? onSwitchToOwner;

  const _BottomNav({
    required this.activeTab,
    required this.onTab,
    this.onSwitchToOwner,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 72,
      decoration: const BoxDecoration(
        color: AppColors.carbon,
        border: Border(top: BorderSide(color: AppColors.smoke)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            _NavItem(
              icon: Icons.search_rounded,
              label: 'Explorar',
              isActive: activeTab == 0,
              onTap: () => onTab(0),
            ),
            _NavItem(
              icon: Icons.calendar_today_rounded,
              label: 'Mis citas',
              isActive: activeTab == 1,
              onTap: () => onTab(1),
            ),
            _NavItem(
              icon: Icons.favorite_rounded,
              label: 'Favoritas',
              isActive: activeTab == 2,
              onTap: () => onTab(2),
            ),
            // Botón switch a modo profesional (solo si tiene ese rol)
            if (onSwitchToOwner != null)
              Expanded(
                child: GestureDetector(
                  onTap: () {
                    HapticFeedback.mediumImpact();
                    onSwitchToOwner!();
                  },
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppSpacing.sm, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.goldSubtle,
                          borderRadius: AppRadius.rPill,
                          border: Border.all(
                            color: AppColors.gold.withOpacity(0.3)),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.storefront_rounded,
                              size: 12, color: AppColors.gold),
                            const SizedBox(width: 3),
                            Text('Negocio',
                              style: AppTypography.overline.copyWith(
                                color: AppColors.gold, fontSize: 9)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isActive;
  final VoidCallback onTap;

  const _NavItem({
    required this.icon,
    required this.label,
    required this.isActive,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 22,
              color: isActive ? AppColors.gold : AppColors.ash),
            const SizedBox(height: 3),
            if (isActive)
              Container(
                width: 4, height: 4,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle, color: AppColors.gold),
              )
            else
              Text(label, style: AppTypography.overline.copyWith(
                fontSize: 8, color: AppColors.ash)),
          ],
        ),
      ),
    );
  }
}

// ─── Widgets helpers ──────────────────────────────────────────────────────────

class _AppointmentList extends StatelessWidget {
  final List<dynamic> appointments;
  final bool showCancelButton;
  final void Function(String)? onCancel;

  const _AppointmentList({
    required this.appointments,
    this.showCancelButton = true,
    this.onCancel,
  });

  @override
  Widget build(BuildContext context) {
    if (appointments.isEmpty) {
      return Center(
        child: Text('Sin citas aquí',
          style: AppTypography.bodyMd.copyWith(color: AppColors.ash)),
      );
    }
    return ListView.builder(
      padding: AppSpacing.screen,
      itemCount: appointments.length,
      itemBuilder: (_, i) => ClientAppointmentCard(
        appointment: appointments[i],
        onCancel: showCancelButton && appointments[i].status.canCancel
            ? () => onCancel?.call(appointments[i].id)
            : null,
      ).animate().fadeIn(delay: Duration(milliseconds: 60 * i)),
    );
  }
}

class _QuickDateSelector extends StatefulWidget {
  final ValueChanged<DateTime> onDate;
  const _QuickDateSelector({required this.onDate});

  @override
  State<_QuickDateSelector> createState() => _QuickDateSelectorState();
}

class _QuickDateSelectorState extends State<_QuickDateSelector> {
  int _selectedIdx = 0;
  late final List<DateTime> _days;

  @override
  void initState() {
    super.initState();
    _days = List.generate(7, (i) => DateTime.now().add(Duration(days: i)));
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 44,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: _days.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppSpacing.sm),
        itemBuilder: (_, i) {
          final isSelected = _selectedIdx == i;
          final label = i == 0 ? 'Hoy' : i == 1 ? 'Mañana' : '${_days[i].day}';

          return GestureDetector(
            onTap: () {
              setState(() => _selectedIdx = i);
              widget.onDate(_days[i]);
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.md, vertical: AppSpacing.sm),
              decoration: BoxDecoration(
                gradient: isSelected ? AppColors.goldGradient : null,
                color: isSelected ? null : AppColors.graphite,
                borderRadius: AppRadius.rPill,
                border: Border.all(
                  color: isSelected ? Colors.transparent : AppColors.smoke),
              ),
              child: Text(label, style: AppTypography.labelMd.copyWith(
                color: isSelected ? AppColors.obsidian : AppColors.silver,
                fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500)),
            ),
          );
        },
      ),
    );
  }
}

class _FavChip extends StatelessWidget {
  final FavoriteBarberia barberia;
  final VoidCallback onTap;

  const _FavChip({required this.barberia, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md, vertical: AppSpacing.sm),
        decoration: BoxDecoration(
          color: AppColors.graphite,
          borderRadius: AppRadius.rMd,
          border: Border.all(
            color: barberia.isOpen
                ? AppColors.gold.withOpacity(0.3) : AppColors.smoke),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(barberia.name, style: AppTypography.labelLg.copyWith(
              fontSize: 12), overflow: TextOverflow.ellipsis),
            const SizedBox(height: 3),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
              decoration: BoxDecoration(
                color: barberia.isOpen
                    ? AppColors.confirmedSubtle : AppColors.errorSubtle,
                borderRadius: AppRadius.rPill,
              ),
              child: Text(barberia.isOpen ? 'Abierto' : 'Cerrado',
                style: AppTypography.overline.copyWith(
                  color: barberia.isOpen
                      ? AppColors.confirmed : AppColors.error,
                  fontSize: 8)),
            ),
          ],
        ),
      ),
    );
  }
}

class _BarberiaCardSkeleton extends StatelessWidget {
  const _BarberiaCardSkeleton();

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(
        AppSpacing.base, 0, AppSpacing.base, AppSpacing.sm),
      height: 100,
      decoration: BoxDecoration(
        color: AppColors.graphite, borderRadius: AppRadius.rCard),
    ).animate(onPlay: (c) => c.repeat())
      .shimmer(duration: 1200.ms, color: AppColors.slate);
  }
}

class _EmptyBarberías extends StatelessWidget {
  const _EmptyBarberías();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.search_off_rounded, size: 40, color: AppColors.ash),
          const SizedBox(height: AppSpacing.md),
          Text('Sin barberías disponibles',
            style: AppTypography.headingSm.copyWith(color: AppColors.silver)),
          const SizedBox(height: AppSpacing.xs),
          Text('Intenta con otra fecha',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash)),
        ],
      ),
    );
  }
}

class _EmptyAppointments extends StatelessWidget {
  const _EmptyAppointments();

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
            child: const Icon(Icons.calendar_today_outlined,
              size: 28, color: AppColors.ash),
          ),
          const SizedBox(height: AppSpacing.md),
          Text('Sin citas aún',
            style: AppTypography.headingSm.copyWith(color: AppColors.silver)),
          const SizedBox(height: AppSpacing.xs),
          Text('Ve a Explorar para reservar',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash)),
        ],
      ),
    );
  }
}

class _EmptyFavorites extends StatelessWidget {
  const _EmptyFavorites();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.favorite_border_rounded,
            size: 40, color: AppColors.ash),
          const SizedBox(height: AppSpacing.md),
          Text('Sin favoritas aún',
            style: AppTypography.headingSm.copyWith(color: AppColors.silver)),
          const SizedBox(height: AppSpacing.xs),
          Text('Toca el ♡ en cualquier barbería',
            style: AppTypography.bodySm.copyWith(color: AppColors.ash)),
        ],
      ),
    );
  }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

String _greeting() {
  final h = DateTime.now().hour;
  if (h < 12) return 'BUENOS DÍAS';
  if (h < 19) return 'BUENAS TARDES';
  return 'BUENAS NOCHES';
}
